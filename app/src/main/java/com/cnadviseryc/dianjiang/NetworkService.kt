package com.cnadviseryc.dianjiang

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.ServerSocket
import java.net.Socket

data class Player(
    val id: String,
    val isHost: Boolean,
    var isReady: Boolean = false
)

data class DrawResult(
    val playerId: String,
    val numbers: List<Int>
)

sealed class NetworkMessage {
    data class PlayerJoined(val player: Player) : NetworkMessage()
    data class PlayerLeft(val playerId: String) : NetworkMessage()
    data class DrawStarted(val results: List<DrawResult>) : NetworkMessage()
    data class JunzhengGameStarted(val results: List<DrawResult>, val roles: Map<String, String>) : NetworkMessage()  // 新增
    data class PlayerReady(val playerId: String) : NetworkMessage()
    data class RoomDismissed(val reason: String) : NetworkMessage()
    data class PlayerList(val players: List<Player>) : NetworkMessage()
    data class RoomConfig(val isJunzhengMode: Boolean) : NetworkMessage()  // 新增：房间配置
}

class NetworkService(private val context: Context) {
    private val TAG = "NetworkService"
    private val SERVICE_TYPE = "_dianjiang._tcp."
    private val SERVICE_NAME = "DianjiangRoom"
    private val PORT = 8888

    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var resolveListener: NsdManager.ResolveListener? = null

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private val connectedClients = mutableListOf<ClientHandler>()

    private val _isHost = MutableStateFlow(false)
    val isHost: StateFlow<Boolean> = _isHost

    private val _players = MutableStateFlow<List<Player>>(emptyList())
    val players: StateFlow<List<Player>> = _players

    private val _networkMessages = MutableStateFlow<NetworkMessage?>(null)
    val networkMessages: StateFlow<NetworkMessage?> = _networkMessages

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var localPlayerId: String = ""
    private var isCleanedUp = false  // 防止重复清理

    // 新增：军争模式标志
    private var isJunzhengMode = false

    // 用于临时发现服务的回调
    private var discoveryCallback: ((Boolean) -> Unit)? = null

    // 新增：设置游戏模式
    fun setJunzhengMode(junzhengMode: Boolean) {
        isJunzhengMode = junzhengMode
    }

    // 启动服务发现（用于检测房间是否存在）
    fun startDiscovery(callback: (Boolean) -> Unit) {
        discoveryCallback = callback

        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "服务发现启动失败: $errorCode")
                callback(false)
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "服务发现停止失败: $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d(TAG, "服务发现已启动，等待发现房间...")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d(TAG, "服务发现已停止")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "✅ 发现服务: ${serviceInfo?.serviceName}")
                if (serviceInfo?.serviceName?.startsWith(SERVICE_NAME) == true) {
                    Log.d(TAG, "✅ 找到点将房间！")
                    callback(true)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "服务丢失: ${serviceInfo?.serviceName}")
            }
        }

        try {
            nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "启动服务发现失败", e)
            callback(false)
        }
    }

    // 停止服务发现
    fun stopDiscovery() {
        try {
            if (discoveryListener != null) {
                nsdManager?.stopServiceDiscovery(discoveryListener)
                discoveryListener = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止服务发现失败", e)
        }
    }

    // 创建房间（房主）
    fun createRoom(playerId: String) {
        localPlayerId = playerId
        _isHost.value = true

        scope.launch {
            try {
                serverSocket = ServerSocket(PORT)
                registerService()

                // 添加房主为第一个玩家（初始状态为未准备）
                val hostPlayer = Player(playerId, true, false)  // 修改：isReady = false
                _players.value = listOf(hostPlayer)

                // 开始接受客户端连接
                acceptClients()
            } catch (e: Exception) {
                Log.e(TAG, "创建房间失败", e)
            }
        }
    }

    // 加入房间（玩家）
    fun joinRoom(playerId: String, callback: (Boolean) -> Unit) {
        localPlayerId = playerId
        _isHost.value = false

        scope.launch {
            try {
                discoverService { serviceInfo ->
                    connectToHost(serviceInfo, playerId, callback)
                }
            } catch (e: Exception) {
                Log.e(TAG, "加入房间失败", e)
                withContext(Dispatchers.Main) {
                    callback(false)
                }
            }
        }
    }

    // 注册NSD服务
    private fun registerService() {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            port = PORT
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "服务注册失败: $errorCode")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "服务注销失败: $errorCode")
            }

            override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "服务注册成功: ${serviceInfo?.serviceName}")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "服务注销成功")
            }
        }

        nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    // 发现NSD服务
    private fun discoverService(onFound: (NsdServiceInfo) -> Unit) {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "服务发现启动失败: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "服务发现停止失败: $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d(TAG, "服务发现已启动")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d(TAG, "服务发现已停止")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "发现服务: ${serviceInfo?.serviceName}")
                if (serviceInfo?.serviceName?.startsWith(SERVICE_NAME) == true) {
                    resolveService(serviceInfo, onFound)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "服务丢失: ${serviceInfo?.serviceName}")
            }
        }

        nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    // 解析服务（适配新API）
    private fun resolveService(serviceInfo: NsdServiceInfo, onResolved: (NsdServiceInfo) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ 使用新API
            nsdManager?.registerServiceInfoCallback(serviceInfo,
                { it.run() },
                object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                        Log.e(TAG, "服务解析失败: $errorCode")
                    }

                    override fun onServiceUpdated(info: NsdServiceInfo) {
                        Log.d(TAG, "服务解析成功: ${info.hostAddresses.firstOrNull()}:${info.port}")
                        onResolved(info)
                    }

                    override fun onServiceLost() {
                        Log.d(TAG, "服务丢失")
                    }

                    override fun onServiceInfoCallbackUnregistered() {
                        Log.d(TAG, "服务回调已注销")
                    }
                })
        } else {
            // Android 13及以下使用旧API
            @Suppress("DEPRECATION")
            resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    Log.e(TAG, "服务解析失败: $errorCode")
                }

                @Suppress("DEPRECATION")
                override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                    Log.d(TAG, "服务解析成功: ${serviceInfo?.host}:${serviceInfo?.port}")
                    serviceInfo?.let { onResolved(it) }
                }
            }

            @Suppress("DEPRECATION")
            nsdManager?.resolveService(serviceInfo, resolveListener)
        }
    }

    // 连接到房主（适配新API）
    private fun connectToHost(serviceInfo: NsdServiceInfo, playerId: String, callback: (Boolean) -> Unit) {
        scope.launch {
            try {
                val host = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    serviceInfo.hostAddresses.firstOrNull()
                } else {
                    @Suppress("DEPRECATION")
                    serviceInfo.host
                }

                if (host == null) {
                    Log.e(TAG, "无法获取主机地址")
                    withContext(Dispatchers.Main) {
                        callback(false)
                    }
                    return@launch
                }

                clientSocket = Socket(host, serviceInfo.port)

                // 发送加入请求
                val message = JSONObject().apply {
                    put("type", "join")
                    put("playerId", playerId)
                }
                sendMessage(message.toString())

                // 开始接收消息
                receiveMessages()

                withContext(Dispatchers.Main) {
                    callback(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "连接房主失败", e)
                withContext(Dispatchers.Main) {
                    callback(false)
                }
            }
        }
    }

    // 接受客户端连接（房主）
    private fun acceptClients() {
        scope.launch {
            while (serverSocket?.isClosed == false) {
                try {
                    val client = serverSocket?.accept()
                    client?.let {
                        val handler = ClientHandler(it)
                        connectedClients.add(handler)
                        handler.start()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "接受客户端失败", e)
                }
            }
        }
    }

    // 客户端处理器
    inner class ClientHandler(val socket: Socket) {
        private val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        private val writer = PrintWriter(socket.getOutputStream(), true)
        var playerId: String = ""

        fun start() {
            scope.launch {
                try {
                    while (socket.isConnected) {
                        val message = reader.readLine() ?: break
                        handleMessage(message, this@ClientHandler)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "客户端断开连接", e)
                } finally {
                    removeClient(this@ClientHandler)
                }
            }
        }

        fun send(message: String) {
            writer.println(message)
        }

        fun close() {
            try {
                writer.close()
                reader.close()
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "关闭客户端连接失败", e)
            }
        }
    }

    // 处理消息
    private fun handleMessage(message: String, client: ClientHandler? = null) {
        try {
            val json = JSONObject(message)
            val type = json.getString("type")

            when (type) {
                "join" -> {
                    val playerId = json.getString("playerId")
                    client?.playerId = playerId

                    val newPlayer = Player(playerId, false, false)
                    val updatedPlayers = _players.value + newPlayer
                    _players.value = updatedPlayers

                    // 通知所有客户端
                    broadcastPlayerList()
                    _networkMessages.value = NetworkMessage.PlayerJoined(newPlayer)

                    // 新增：向新加入的玩家发送房间配置
                    if (client != null) {
                        val configMessage = JSONObject().apply {
                            put("type", "roomConfig")
                            put("isJunzhengMode", isJunzhengMode)
                        }
                        client.send(configMessage.toString())
                        Log.d(TAG, "向玩家 $playerId 发送房间配置: isJunzhengMode=$isJunzhengMode")
                    }
                }

                "playerList" -> {
                    val playersArray = json.getJSONArray("players")
                    val playersList = mutableListOf<Player>()
                    for (i in 0 until playersArray.length()) {
                        val playerObj = playersArray.getJSONObject(i)
                        playersList.add(Player(
                            playerObj.getString("id"),
                            playerObj.getBoolean("isHost"),
                            playerObj.getBoolean("isReady")
                        ))
                    }
                    _players.value = playersList
                    _networkMessages.value = NetworkMessage.PlayerList(playersList)
                }

                "draw" -> {
                    val resultsArray = json.getJSONArray("results")
                    val results = mutableListOf<DrawResult>()
                    for (i in 0 until resultsArray.length()) {
                        val resultObj = resultsArray.getJSONObject(i)
                        val numbersArray = resultObj.getJSONArray("numbers")
                        val numbers = mutableListOf<Int>()
                        for (j in 0 until numbersArray.length()) {
                            numbers.add(numbersArray.getInt(j))
                        }
                        results.add(DrawResult(resultObj.getString("playerId"), numbers))
                    }
                    _networkMessages.value = NetworkMessage.DrawStarted(results)
                }

                // 新增：处理军争模式游戏开始
                "junzheng_game" -> {
                    val resultsArray = json.getJSONArray("results")
                    val results = mutableListOf<DrawResult>()
                    for (i in 0 until resultsArray.length()) {
                        val resultObj = resultsArray.getJSONObject(i)
                        val numbersArray = resultObj.getJSONArray("numbers")
                        val numbers = mutableListOf<Int>()
                        for (j in 0 until numbersArray.length()) {
                            numbers.add(numbersArray.getInt(j))
                        }
                        results.add(DrawResult(resultObj.getString("playerId"), numbers))
                    }

                    // 解析角色分配
                    val rolesObj = json.getJSONObject("roles")
                    val roles = mutableMapOf<String, String>()
                    rolesObj.keys().forEach { key ->
                        roles[key] = rolesObj.getString(key)
                    }

                    _networkMessages.value = NetworkMessage.JunzhengGameStarted(results, roles)
                }

                "ready" -> {
                    val playerId = json.getString("playerId")
                    updatePlayerReady(playerId)
                    _networkMessages.value = NetworkMessage.PlayerReady(playerId)
                }

                "dismiss" -> {
                    val reason = json.optString("reason", "房主已解散房间")
                    _networkMessages.value = NetworkMessage.RoomDismissed(reason)
                }

                // 新增：接收房间配置
                "roomConfig" -> {
                    val isJunzhengMode = json.getBoolean("isJunzhengMode")
                    _networkMessages.value = NetworkMessage.RoomConfig(isJunzhengMode)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理消息失败", e)
        }
    }

    // 发送消息（客户端）
    private fun sendMessage(message: String) {
        scope.launch {
            try {
                clientSocket?.getOutputStream()?.let { outputStream ->
                    val writer = PrintWriter(outputStream, true)
                    writer.println(message)
                }
            } catch (e: Exception) {
                Log.e(TAG, "发送消息失败", e)
            }
        }
    }

    // 接收消息（客户端）
    private fun receiveMessages() {
        scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(clientSocket?.getInputStream()))
                while (clientSocket?.isConnected == true) {
                    val message = reader.readLine() ?: break
                    handleMessage(message)
                }
            } catch (e: Exception) {
                Log.e(TAG, "接收消息失败", e)
            }
        }
    }

    // 广播玩家列表
    private fun broadcastPlayerList() {
        scope.launch {
            val message = JSONObject().apply {
                put("type", "playerList")
                put("players", JSONArray().apply {
                    _players.value.forEach { player ->
                        put(JSONObject().apply {
                            put("id", player.id)
                            put("isHost", player.isHost)
                            put("isReady", player.isReady)
                        })
                    }
                })
            }
            broadcast(message.toString())
        }
    }

    // 广播消息
    private fun broadcast(message: String) {
        scope.launch {
            connectedClients.forEach {
                try {
                    it.send(message)
                } catch (e: Exception) {
                    Log.e(TAG, "广播消息到客户端失败", e)
                }
            }
        }
    }

    // 房主开始抽取（国战模式）
    fun startDraw(count: Int, totalImages: Int) {
        if (!_isHost.value) return

        scope.launch {
            val players = _players.value
            val totalCount = count * players.size

            if (totalCount > totalImages) return@launch

            // 随机抽取数字
            val allNumbers = (1..totalImages).shuffled().take(totalCount)

            // 分配给每个玩家
            val results = players.mapIndexed { index, player ->
                val startIdx = index * count
                val endIdx = startIdx + count
                DrawResult(player.id, allNumbers.subList(startIdx, endIdx))
            }

            // 广播抽取结果
            val message = JSONObject().apply {
                put("type", "draw")
                put("results", JSONArray().apply {
                    results.forEach { result ->
                        put(JSONObject().apply {
                            put("playerId", result.playerId)
                            put("numbers", JSONArray(result.numbers))
                        })
                    }
                })
            }
            broadcast(message.toString())

            // 本地处理
            withContext(Dispatchers.Main) {
                _networkMessages.value = NetworkMessage.DrawStarted(results)
            }
        }
    }

    // 新增：房主开始军争模式游戏
    fun startJunzhengGame(playerRoles: Map<String, String>, cardsPerPlayer: Int, totalImages: Int) {
        if (!_isHost.value) return

        scope.launch {
            val players = _players.value
            val totalCount = cardsPerPlayer * players.size

            if (totalCount > totalImages) return@launch

            // 随机抽取数字
            val allNumbers = (1..totalImages).shuffled().take(totalCount)

            // 分配给每个玩家
            val results = players.mapIndexed { index, player ->
                val startIdx = index * cardsPerPlayer
                val endIdx = startIdx + cardsPerPlayer
                DrawResult(player.id, allNumbers.subList(startIdx, endIdx))
            }

            // 广播游戏开始结果（包含角色信息）
            val message = JSONObject().apply {
                put("type", "junzheng_game")
                put("results", JSONArray().apply {
                    results.forEach { result ->
                        put(JSONObject().apply {
                            put("playerId", result.playerId)
                            put("numbers", JSONArray(result.numbers))
                        })
                    }
                })
                put("roles", JSONObject(playerRoles))
            }
            broadcast(message.toString())

            // 本地处理
            withContext(Dispatchers.Main) {
                _networkMessages.value = NetworkMessage.JunzhengGameStarted(results, playerRoles)
            }
        }
    }

    // 玩家准备完成
    fun setPlayerReady() {
        scope.launch {
            updatePlayerReady(localPlayerId)

            val message = JSONObject().apply {
                put("type", "ready")
                put("playerId", localPlayerId)
            }

            if (_isHost.value) {
                broadcast(message.toString())
            } else {
                sendMessage(message.toString())
            }
        }
    }

    // 更新玩家准备状态
    private fun updatePlayerReady(playerId: String) {
        _players.value = _players.value.map {
            if (it.id == playerId) it.copy(isReady = true) else it
        }
        broadcastPlayerList()
    }

    // 重置所有玩家准备状态
    fun resetAllReady() {
        scope.launch {
            _players.value = _players.value.map { it.copy(isReady = false) }
            broadcastPlayerList()
        }
    }

    // 移除客户端
    private fun removeClient(client: ClientHandler) {
        connectedClients.remove(client)
        val playerId = client.playerId
        if (playerId.isNotEmpty()) {
            _players.value = _players.value.filter { it.id != playerId }
            broadcastPlayerList()
            _networkMessages.value = NetworkMessage.PlayerLeft(playerId)
        }
    }

    // 解散房间
    fun dismissRoom() {
        if (isCleanedUp) return

        scope.launch {
            val message = JSONObject().apply {
                put("type", "dismiss")
                put("reason", "房主已解散房间")
            }
            broadcast(message.toString())

            // 等待消息发送完成
            delay(500)
            cleanup()
        }
    }

    // 退出房间
    fun leaveRoom() {
        if (isCleanedUp) return
        cleanup()
    }

    // 清理资源
    private fun cleanup() {
        if (isCleanedUp) {
            Log.d(TAG, "资源已经清理过，跳过")
            return
        }

        isCleanedUp = true

        try {
            // 只注销已注册的监听器
            if (registrationListener != null) {
                try {
                    nsdManager?.unregisterService(registrationListener)
                    Log.d(TAG, "NSD服务已注销")
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "注销服务监听器失败: 监听器未注册", e)
                } catch (e: Exception) {
                    Log.e(TAG, "注销服务监听器失败", e)
                }
                registrationListener = null
            }

            if (discoveryListener != null) {
                try {
                    nsdManager?.stopServiceDiscovery(discoveryListener)
                    Log.d(TAG, "NSD服务发现已停止")
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "停止服务发现失败: 监听器未注册", e)
                } catch (e: Exception) {
                    Log.e(TAG, "停止服务发现失败", e)
                }
                discoveryListener = null
            }

            // 关闭所有客户端连接
            connectedClients.forEach {
                try {
                    it.close()
                } catch (e: Exception) {
                    Log.e(TAG, "关闭客户端连接失败", e)
                }
            }
            connectedClients.clear()

            // 关闭服务器Socket
            try {
                serverSocket?.close()
                serverSocket = null
            } catch (e: Exception) {
                Log.e(TAG, "关闭服务器Socket失败", e)
            }

            // 关闭客户端Socket
            try {
                clientSocket?.close()
                clientSocket = null
            } catch (e: Exception) {
                Log.e(TAG, "关闭客户端Socket失败", e)
            }

            // 取消协程作用域
            try {
                scope.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "取消协程失败", e)
            }

            Log.d(TAG, "资源清理完成")
        } catch (e: Exception) {
            Log.e(TAG, "清理资源时发生未预期的错误", e)
        }
    }
}

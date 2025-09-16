package com.cnadviseryc.wjxq.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper

import java.net.NetworkInterface
import java.net.URI
import java.util.*
import java.net.ServerSocket
import java.io.IOException
import kotlin.math.absoluteValue

class NetworkManager(private val context: Context) {
    private var gameServer: GameServer? = null
    private var gameClient: GameClient? = null
    private var isHost = false
    private val playerId = UUID.randomUUID().toString()
    
    // NSD相关变量
    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var nsdServiceInfo: NsdServiceInfo? = null
    private var isServiceRegistered = false
    private var isDiscovering = false
    private val discoveredServices = mutableListOf<NsdServiceInfo>()
    
    // NSD服务类型和名称
    private companion object {
        const val SERVICE_TYPE = "_wjxq._tcp."
        const val SERVICE_NAME_PREFIX = "WJXQ_Room_"
    }
    
    // 回调函数
    var onRoomCreated: ((String) -> Unit)? = null
    var onRoomJoined: ((RoomInfo) -> Unit)? = null
    var onPlayerJoined: ((PlayerInfo) -> Unit)? = null
    var onPlayerLeft: ((String) -> Unit)? = null
    var onGameStarted: ((GameStartData) -> Unit)? = null
    var onAllSelectionsComplete: (() -> Unit)? = null
    var onPlayerStatusChanged: ((RoomInfo) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onRoomDiscovered: ((List<NsdServiceInfo>) -> Unit)? = null
    
    fun createRoom(playerName: String): String {
        try {
            println("[NSD创建房间] 开始创建房间，玩家名: $playerName")
            stopAll()
            
            // 使用默认端口8080
            val targetPort = 8080
            println("[NSD创建房间] 使用端口: $targetPort")
            
            // 检查端口是否可用
            if (!isPortAvailable(targetPort)) {
                throw Exception("端口 $targetPort 被占用，请稍后重试")
            }
            
            // 创建游戏服务器
            println("[NSD创建房间] 创建GameServer...")
            val server = GameServer(targetPort)
            server.start()
            println("[NSD创建房间] 服务器创建并启动成功")
            
            gameServer = server
            isHost = true
            
            // 设置服务器回调
            server.onRoomCreated = { createdRoomId ->
                // 服务器创建成功后注册NSD服务
                registerNsdService(createdRoomId, targetPort, playerName)
                onRoomCreated?.invoke(createdRoomId)
            }
            
            server.onPlayerJoined = { playerInfo ->
                onPlayerJoined?.invoke(playerInfo)
            }
            
            server.onPlayerLeft = { playerId ->
                onPlayerLeft?.invoke(playerId)
            }
            
            server.onGameStarted = { gameStartData ->
                onGameStarted?.invoke(gameStartData)
            }
            
            server.onAllSelectionsComplete = {
                onAllSelectionsComplete?.invoke()
            }
            
            // 房主也需要作为客户端连接到自己的服务器
            val client = GameClient(URI("ws://localhost:$targetPort"))
            gameClient = client
            
            client.onRoomJoined = { roomInfo ->
                onRoomJoined?.invoke(roomInfo)
            }
            
            client.onError = { error ->
                onError?.invoke(error)
            }
            
            client.connect()
            
            // 等待连接建立后加入房间
            Thread.sleep(1000)
            val playerInfo = PlayerInfo(
                playerId = playerId,
                playerName = playerName,
                isHost = true
            )
            client.joinRoom(playerInfo, "")
            
            return "NSD_ROOM_CREATED" // NSD模式下返回特殊标识
        } catch (e: Exception) {
            onError?.invoke("创建房间失败: ${e.message}")
            return ""
        }
    }
    
    fun joinRoom(playerName: String) {
        try {
            println("[NSD加入房间] 开始寻找房间，玩家名: $playerName")
            stopAll()
            
            // 使用NSD发现服务
            discoverNsdServices(playerName)
            
        } catch (e: Exception) {
            onError?.invoke("加入房间失败: ${e.message}")
        }
    }
    
    fun startGame(drawCount: Int) {
        if (!isHost) {
            onError?.invoke("只有房主可以开始游戏")
            return
        }
        
        val gameStartData = GameStartData(
            drawCount = drawCount,
            playerImages = emptyMap() // 服务器会重新分配
        )
        
        val message = NetworkMessage(
            type = MessageType.START_GAME,
            data = JsonUtils.toJson(gameStartData),
            playerId = playerId
        )
        
        gameClient?.send(JsonUtils.toJson(message))
    }
    
    fun sendSelectionComplete(selectedImages: List<Int>) {
        gameClient?.sendSelectionComplete(playerId, selectedImages)
    }
    
    fun resetGame() {
        if (isHost) {
            gameServer?.resetGame()
        }
    }
    
    fun changePlayerStatus(status: PlayerStatus) {
        val statusChangeData = PlayerStatusChangeData(
            playerId = playerId,
            status = status
        )
        
        val message = NetworkMessage(
            type = MessageType.PLAYER_STATUS_CHANGED,
            data = JsonUtils.toJson(statusChangeData),
            playerId = playerId
        )
        
        if (isHost) {
            gameServer?.handlePlayerStatusChange(message)
        } else {
            gameClient?.send(JsonUtils.toJson(message))
        }
    }
    

    
    fun getPlayerId(): String = playerId
    
    fun isHost(): Boolean = isHost
    
    // getLocalIpAddress函数已删除，NSD会自动处理IP地址
     
     // NSD相关变量
     private var pendingPlayerName: String? = null
     
     // NSD服务注册监听器
    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            println("[NSD注册] 服务注册成功: ${serviceInfo.serviceName}")
            nsdServiceInfo = serviceInfo
            isServiceRegistered = true
        }
        
        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            println("[NSD注册] 服务注册失败: $errorCode")
            onError?.invoke("注册房间服务失败，错误代码: $errorCode")
        }
        
        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
            println("[NSD注册] 服务注销成功: ${serviceInfo.serviceName}")
            isServiceRegistered = false
            nsdServiceInfo = null
        }
        
        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            println("[NSD注册] 服务注销失败: $errorCode")
        }
    }
    
    // NSD服务发现监听器
    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            println("[NSD发现] 开始发现失败: $errorCode")
            onError?.invoke("开始发现房间失败，错误代码: $errorCode")
        }
        
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            println("[NSD发现] 停止发现失败: $errorCode")
        }
        
        override fun onDiscoveryStarted(serviceType: String) {
            println("[NSD发现] 开始发现服务: $serviceType")
            isDiscovering = true
        }
        
        override fun onDiscoveryStopped(serviceType: String) {
            println("[NSD发现] 停止发现服务: $serviceType")
            isDiscovering = false
        }
        
        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            println("[NSD发现] 发现服务: ${serviceInfo.serviceName}")
            if (serviceInfo.serviceName.startsWith(SERVICE_NAME_PREFIX)) {
                // 解析服务详情
                nsdManager.resolveService(serviceInfo, resolveListener)
            }
        }
        
        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            println("[NSD发现] 服务丢失: ${serviceInfo.serviceName}")
            discoveredServices.removeAll { it.serviceName == serviceInfo.serviceName }
            mainHandler.post {
                onRoomDiscovered?.invoke(discoveredServices.toList())
            }
        }
    }
    
    // NSD服务解析监听器
    private val resolveListener = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            println("[NSD解析] 解析服务失败: ${serviceInfo.serviceName}, 错误代码: $errorCode")
        }
        
        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            println("[NSD解析] 解析服务成功: ${serviceInfo.serviceName}")
            println("[NSD解析] 主机: ${serviceInfo.host}, 端口: ${serviceInfo.port}")
            
            // 添加到发现的服务列表
            discoveredServices.removeAll { it.serviceName == serviceInfo.serviceName }
            discoveredServices.add(serviceInfo)
            
            mainHandler.post {
                onRoomDiscovered?.invoke(discoveredServices.toList())
                
                // 如果只有一个房间，自动连接
                if (discoveredServices.size == 1 && pendingPlayerName != null) {
                    connectToService(serviceInfo, pendingPlayerName!!)
                    pendingPlayerName = null
                    stopDiscovery()
                }
            }
        }
    }
    
    // NSD服务注册
    private fun registerNsdService(roomId: String, port: Int, hostName: String) {
        try {
            println("[NSD注册] 开始注册服务，房间ID: $roomId, 端口: $port, 房主: $hostName")
            
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "$SERVICE_NAME_PREFIX$roomId"
                serviceType = SERVICE_TYPE
                this.port = port
                // 添加房主名称到TXT记录
                setAttribute("host", hostName)
                setAttribute("roomId", roomId)
            }
            
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
            
        } catch (e: Exception) {
            println("[NSD注册] 注册服务失败: ${e.message}")
            onError?.invoke("注册房间服务失败: ${e.message}")
        }
    }
    
    // NSD服务发现
    private fun discoverNsdServices(playerName: String) {
        try {
            println("[NSD发现] 开始发现服务...")
            this.pendingPlayerName = playerName
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            
        } catch (e: Exception) {
            println("[NSD发现] 发现服务失败: ${e.message}")
            onError?.invoke("发现房间失败: ${e.message}")
        }
    }
    
    // 停止服务发现
    private fun stopDiscovery() {
        try {
            if (isDiscovering) {
                nsdManager.stopServiceDiscovery(discoveryListener)
            }
        } catch (e: Exception) {
            println("[NSD发现] 停止发现失败: ${e.message}")
        }
    }
    
    // 连接到发现的服务
    private fun connectToService(serviceInfo: NsdServiceInfo, playerName: String) {
        try {
            val host = serviceInfo.host?.hostAddress ?: "localhost"
            val port = serviceInfo.port
            
            println("[NSD连接] 连接到服务: $host:$port")
            
            val client = GameClient(URI("ws://$host:$port"))
            gameClient = client
            isHost = false
            
            client.onRoomJoined = { roomInfo ->
                onRoomJoined?.invoke(roomInfo)
            }
            
            client.onPlayerJoined = { roomInfo ->
                onRoomJoined?.invoke(roomInfo)
            }
            
            client.onPlayerLeft = { roomInfo ->
                onRoomJoined?.invoke(roomInfo)
            }
            
            client.onGameStarted = { gameStartData ->
                onGameStarted?.invoke(gameStartData)
            }
            
            client.onAllSelectionsComplete = {
                onAllSelectionsComplete?.invoke()
            }
            
            client.onPlayerStatusChanged = { roomInfo ->
                onPlayerStatusChanged?.invoke(roomInfo)
            }
            
            client.onError = { error ->
                onError?.invoke(error)
            }
            
            client.connect()
            
            // 等待连接建立后加入房间
            Thread.sleep(1000)
            val playerInfo = PlayerInfo(
                playerId = playerId,
                playerName = playerName,
                isHost = false
            )
            client.joinRoom(playerInfo, "")
            
        } catch (e: Exception) {
            println("[NSD连接] 连接失败: ${e.message}")
            onError?.invoke("连接房间失败: ${e.message}")
        }
    }
    
    private fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).use { true }
        } catch (_: IOException) {
            false
        }
    }
    

    
    private fun forceReleasePort(port: Int) {
        try {
            val isWindows = System.getProperty("os.name")?.lowercase()?.contains("windows") == true
            
            if (isWindows) {
                // Windows: 查找并杀死占用端口的进程
                val findProcess = Runtime.getRuntime().exec("netstat -ano | findstr :$port")
                findProcess.waitFor()
                val output = findProcess.inputStream.bufferedReader().readText()
                
                // 解析PID并杀死进程
                val lines = output.split("\n")
                for (line in lines) {
                    if (line.contains(":$port") && line.contains("LISTENING")) {
                        val parts = line.trim().split("\\s+".toRegex())
                        if (parts.size >= 5) {
                            val pid = parts[4]
                            try {
                                Runtime.getRuntime().exec("taskkill /F /PID $pid").waitFor()
                                println("已强制结束进程 PID: $pid")
                            } catch (e: Exception) {
                                println("无法结束进程 PID: $pid")
                            }
                        }
                    }
                }
            } else {
                // Linux/Mac: 使用lsof和kill
                val findProcess = Runtime.getRuntime().exec("lsof -ti:$port")
                findProcess.waitFor()
                val pids = findProcess.inputStream.bufferedReader().readText().trim()
                
                if (pids.isNotEmpty()) {
                    val pidList = pids.split("\n")
                    for (pid in pidList) {
                        if (pid.isNotEmpty()) {
                            try {
                                Runtime.getRuntime().exec("kill -9 $pid").waitFor()
                                println("已强制结束进程 PID: $pid")
                            } catch (e: Exception) {
                                println("无法结束进程 PID: $pid")
                            }
                        }
                    }
                }
            }
            
            // 等待端口释放
            Thread.sleep(500)
        } catch (e: Exception) {
            println("强制释放端口失败: ${e.message}")
        }
    }
    
    fun stopAll() {
        try {
            // 停止NSD服务发现
            stopDiscovery()
            
            // 注销NSD服务
            if (isServiceRegistered && nsdServiceInfo != null) {
                try {
                    nsdManager.unregisterService(registrationListener)
                } catch (e: Exception) {
                    println("[NSD] 注销服务失败: ${e.message}")
                }
            }
            
            // 清理NSD相关状态
            discoveredServices.clear()
            pendingPlayerName = null
            
            // 先关闭客户端连接
            gameClient?.close()
            gameClient = null
            
            // 停止服务器并等待完全关闭
            try {
                gameServer?.stop(1000) // 等待1秒
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                gameServer = null
            }
            
            isHost = false
            
            // 额外等待确保资源完全释放
            Thread.sleep(1000)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
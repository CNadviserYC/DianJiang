package com.cnadviseryc.dianjiang

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.io.File

class RoomActivity : AppCompatActivity() {
    private lateinit var networkService: NetworkService
    private lateinit var playerListView: RecyclerView
    private lateinit var playerAdapter: PlayerAdapter
    private lateinit var countInput: EditText
    private lateinit var startButton: Button
    private lateinit var exitButton: Button
    private lateinit var statusText: TextView
    private lateinit var titleText: TextView

    // 新增：军争模式相关控件
    private lateinit var roleInputPanel: LinearLayout
    private lateinit var zhugongInput: EditText
    private lateinit var fanzeiInput: EditText
    private lateinit var neijianInput: EditText
    private lateinit var zhongchenInput: EditText
    private lateinit var junzhengCountInput: EditText  // 军争模式的抽取牌数

    private var imageFiles = mutableListOf<File>()
    private var myDrawNumbers = listOf<Int>()
    private var isInDrawing = false
    private var hasLeftRoom = false

    // 新增：军争模式相关变量
    private var isJunzhengMode = false
    private val playerRoles = mutableMapOf<String, String>() // 玩家ID -> 角色身份
    private var playerId: String = ""

    private val selectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        networkService.setPlayerReady()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room)

        // 获取Intent参数
        playerId = intent.getStringExtra("playerId") ?: ""
        isJunzhengMode = intent.getBooleanExtra("isJunzhengMode", false)  // 新增

        // 修改：接收文件路径并转换为File对象
        val imagePaths = intent.getStringArrayListExtra("imagePaths")
        imagePaths?.forEach { path ->
            imageFiles.add(File(path))
        }

        android.util.Log.d("RoomActivity", "onCreate - playerId: $playerId")
        android.util.Log.d("RoomActivity", "onCreate - isJunzhengMode: $isJunzhengMode")
        android.util.Log.d("RoomActivity", "onCreate - imageFiles数量: ${imageFiles.size}")

        // 初始化控件
        titleText = findViewById(R.id.titleText)
        playerListView = findViewById(R.id.playerListView)
        countInput = findViewById(R.id.countInput)
        startButton = findViewById(R.id.startButton)
        exitButton = findViewById(R.id.exitButton)
        statusText = findViewById(R.id.statusText)

        // 新增：初始化军争模式控件
        roleInputPanel = findViewById(R.id.roleInputPanel)
        zhugongInput = findViewById(R.id.zhugongInput)
        fanzeiInput = findViewById(R.id.fanzeiInput)
        neijianInput = findViewById(R.id.neijianInput)
        zhongchenInput = findViewById(R.id.zhongchenInput)
        junzhengCountInput = findViewById(R.id.junzhengCountInput)

        // 新增：根据模式显示/隐藏控件
        if (isJunzhengMode) {
            roleInputPanel.visibility = View.VISIBLE
            countInput.visibility = View.GONE
            titleText.text = "军争模式房间"
        } else {
            roleInputPanel.visibility = View.GONE
            countInput.visibility = View.VISIBLE
            titleText.text = "国战模式房间"
        }

        networkService = NetworkService(this)
        networkService.localPlayerId = playerId
        networkService.setJunzhengMode(isJunzhengMode)  // 新增：设置网络服务的模式

        playerAdapter = PlayerAdapter()
        playerListView.layoutManager = LinearLayoutManager(this)
        playerListView.adapter = playerAdapter

        setupButtons()
        observeNetworkState()

        checkAndJoinRoom(playerId)
    }

    private fun setupButtons() {
        startButton.setOnClickListener {
            if (networkService.isHost.value) {
                if (isJunzhengMode) {
                    startJunzhengMode()
                } else {
                    startGuozhanMode()
                }
            }
        }

        exitButton.setOnClickListener {
            showExitDialog()
        }
    }

    // 新增：国战模式开始方法
    private fun startGuozhanMode() {
        val count = countInput.text.toString().toIntOrNull()
        if (count == null || count <= 0) {
            Toast.makeText(this, "请输入有效的抽取个数", Toast.LENGTH_SHORT).show()
            return
        }

        if (imageFiles.isEmpty()) {
            Toast.makeText(this, "请先上传将包", Toast.LENGTH_SHORT).show()
            return
        }

        val totalCount = count * networkService.players.value.size
        if (totalCount > imageFiles.size) {
            Toast.makeText(this, "武将数量不足", Toast.LENGTH_SHORT).show()
            return
        }

        isInDrawing = true
        startButton.isEnabled = false
        networkService.startDraw(count, imageFiles.size)
    }

    // 新增：军争模式开始方法
    private fun startJunzhengMode() {
        // 获取身份牌分配数
        val zhugong = zhugongInput.text.toString().toIntOrNull() ?: 0
        val fanzei = fanzeiInput.text.toString().toIntOrNull() ?: 0
        val neijian = neijianInput.text.toString().toIntOrNull() ?: 0
        val zhongchen = zhongchenInput.text.toString().toIntOrNull() ?: 0

        // 获取抽取牌数
        val count = junzhengCountInput.text.toString().toIntOrNull()
        if (count == null || count <= 0) {
            Toast.makeText(this, "请输入有效的抽取个数", Toast.LENGTH_SHORT).show()
            return
        }

        val totalRoles = zhugong + fanzei + neijian + zhongchen
        val playerCount = networkService.players.value.size

        if (totalRoles != playerCount) {
            Toast.makeText(this, "身份牌总数($totalRoles)必须等于玩家人数($playerCount)", Toast.LENGTH_SHORT).show()
            return
        }

        if (zhugong <= 0) {
            Toast.makeText(this, "至少需要1个主公", Toast.LENGTH_SHORT).show()
            return
        }

        if (imageFiles.isEmpty()) {
            Toast.makeText(this, "请先上传将包", Toast.LENGTH_SHORT).show()
            return
        }

        // 检查武将牌数量是否足够
        val totalCount = count * playerCount
        if (totalCount > imageFiles.size) {
            Toast.makeText(this, "武将数量不足", Toast.LENGTH_SHORT).show()
            return
        }

        // 分配身份牌
        val roles = mutableListOf<String>()
        repeat(zhugong) { roles.add("zhugong") }
        repeat(fanzei) { roles.add("fanzei") }
        repeat(neijian) { roles.add("neijian") }
        repeat(zhongchen) { roles.add("zhongchen") }
        roles.shuffle()  // 随机打乱角色顺序

        // 给每个玩家分配身份
        val players = networkService.players.value
        playerRoles.clear()
        players.forEachIndexed { index, player ->
            playerRoles[player.id] = roles[index]
            android.util.Log.d("RoomActivity", "分配身份: ${player.id} -> ${roles[index]}")
        }

        android.util.Log.d("RoomActivity", "所有身份分配完成，playerRoles: $playerRoles")
        android.util.Log.d("RoomActivity", "当前playerId: $playerId")
        android.util.Log.d("RoomActivity", "当前playerId的身份: ${playerRoles[playerId]}")

        // 开始游戏
        isInDrawing = true
        startButton.isEnabled = false
        networkService.startJunzhengGame(playerRoles, count, imageFiles.size) // 传递抽取牌数
    }

    private fun observeNetworkState() {
        lifecycleScope.launch {
            networkService.isHost.collect { isHost ->
                runOnUiThread {
                    // 修改：根据模式显示不同控件
                    if (isJunzhengMode) {
                        roleInputPanel.visibility = if (isHost) View.VISIBLE else View.GONE
                        countInput.visibility = View.GONE
                    } else {
                        roleInputPanel.visibility = View.GONE
                        countInput.visibility = if (isHost) View.VISIBLE else View.GONE
                    }
                    startButton.visibility = if (isHost) View.VISIBLE else View.GONE
                    statusText.text = if (isHost) "你是房主" else "等待房主开始..."
                }
            }
        }

        lifecycleScope.launch {
            networkService.players.collect { players ->
                runOnUiThread {
                    playerAdapter.updatePlayers(players)

                    // 检查是否所有人都准备完成
                    if (isInDrawing && players.all { it.isReady }) {
                        // 所有人退出选择界面，房主可以重新抽取
                        if (networkService.isHost.value) {
                            isInDrawing = false
                            startButton.isEnabled = true
                            networkService.resetAllReady()
                            Toast.makeText(this@RoomActivity, "所有人已完成，可以重新抽取", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            networkService.networkMessages.collect { message ->
                when (message) {
                    is NetworkMessage.PlayerJoined -> {
                        runOnUiThread {
                            Toast.makeText(this@RoomActivity, "${message.player.id} 加入房间", Toast.LENGTH_SHORT).show()
                        }
                    }
                    is NetworkMessage.PlayerLeft -> {
                        runOnUiThread {
                            Toast.makeText(this@RoomActivity, "${message.playerId} 离开房间", Toast.LENGTH_SHORT).show()
                        }
                    }
                    is NetworkMessage.DrawStarted -> {
                        val myResult = message.results.find { it.playerId == networkService.localPlayerId }
                        myResult?.let {
                            myDrawNumbers = it.numbers
                            showSelectionScreen()
                        }
                    }
                    // 新增：处理军争模式消息
                    is NetworkMessage.JunzhengGameStarted -> {
                        val myResult = message.results.find { it.playerId == networkService.localPlayerId }
                        myResult?.let {
                            myDrawNumbers = it.numbers

                            // 只有非房主才需要从消息中获取角色
                            if (!networkService.isHost.value) {
                                playerRoles.clear()
                                playerRoles.putAll(message.roles)
                                android.util.Log.d("RoomActivity", "非房主接收角色: $playerRoles")
                            } else {
                                android.util.Log.d("RoomActivity", "房主已有角色，不清空: $playerRoles")
                            }

                            showSelectionScreen()
                        }
                    }
                    is NetworkMessage.RoomDismissed -> {
                        runOnUiThread {
                            Toast.makeText(this@RoomActivity, message.reason, Toast.LENGTH_LONG).show()
                            finish()
                        }
                    }
                    // 新增：处理房间配置消息
                    is NetworkMessage.RoomConfig -> {
                        runOnUiThread {
                            android.util.Log.d("RoomActivity", "接收到房间配置: isJunzhengMode=${message.isJunzhengMode}")
                            isJunzhengMode = message.isJunzhengMode
                            // 更新UI显示
                            if (isJunzhengMode) {
                                titleText.text = "军争模式房间"
                            } else {
                                titleText.text = "国战模式房间"
                            }
                        }
                    }
                    // 新增：处理加入错误
                    is NetworkMessage.JoinError -> {
                        runOnUiThread {
                            Toast.makeText(this@RoomActivity, message.reason, Toast.LENGTH_LONG).show()
                            // 返回主界面重新输入ID
                            finish()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun checkAndJoinRoom(playerId: String) {
        val shouldCreateRoom = intent.getBooleanExtra("createRoom", true)

        lifecycleScope.launch {
            kotlinx.coroutines.delay(500)

            if (shouldCreateRoom) {
                networkService.createRoom(playerId)
                runOnUiThread {
                    Toast.makeText(this@RoomActivity, "房间已创建", Toast.LENGTH_SHORT).show()
                }
            } else {
                networkService.joinRoom(playerId) { success ->
                    runOnUiThread {
                        if (success) {
                            Toast.makeText(this@RoomActivity, "加入房间成功", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@RoomActivity, "加入房间失败，尝试创建新房间", Toast.LENGTH_LONG).show()
                            networkService.createRoom(playerId)
                        }
                    }
                }
            }
        }
    }

    private fun showSelectionScreen() {
        val availableImages = imageFiles.filter { file ->
            val number = file.nameWithoutExtension.toIntOrNull()
            number != null && number in myDrawNumbers
        }

        if (availableImages.isEmpty()) {
            Toast.makeText(this, "没有对应的图片", Toast.LENGTH_SHORT).show()
            networkService.setPlayerReady()
            return
        }

        android.util.Log.d("RoomActivity", "showSelectionScreen - isJunzhengMode: $isJunzhengMode")
        android.util.Log.d("RoomActivity", "showSelectionScreen - playerId: $playerId")
        android.util.Log.d("RoomActivity", "showSelectionScreen - networkService.localPlayerId: ${networkService.localPlayerId}")
        android.util.Log.d("RoomActivity", "showSelectionScreen - playerRole: ${playerRoles[playerId]}")
        android.util.Log.d("RoomActivity", "showSelectionScreen - playerRoles全部: $playerRoles")

        val intent = Intent(this, SelectionActivity::class.java)
        intent.putStringArrayListExtra("imagePaths",
            ArrayList(availableImages.map { it.absolutePath }))
        intent.putExtra("isNetworkMode", true)

        // 新增：传递军争模式相关参数
        intent.putExtra("isJunzhengMode", isJunzhengMode)
        if (isJunzhengMode) {
            // 容错处理：先尝试用 playerId，如果为 null 则尝试用 networkService.localPlayerId
            var role = playerRoles[playerId]
            if (role == null) {
                android.util.Log.w("RoomActivity", "警告: playerRoles[$playerId] 为 null，尝试使用 localPlayerId")
                role = playerRoles[networkService.localPlayerId]
            }

            if (role == null) {
                android.util.Log.e("RoomActivity", "错误: 无法找到身份信息！")
                android.util.Log.e("RoomActivity", "playerId: $playerId")
                android.util.Log.e("RoomActivity", "localPlayerId: ${networkService.localPlayerId}")
                android.util.Log.e("RoomActivity", "playerRoles: $playerRoles")
                Toast.makeText(this, "错误: 无法获取身份信息", Toast.LENGTH_LONG).show()
                return
            }

            android.util.Log.d("RoomActivity", "最终使用的身份: $role")
            intent.putExtra("playerRole", role)
            intent.putExtra("maxSelection", 1) // 军争模式只能选1张
        } else {
            intent.putExtra("maxSelection", 2) // 国战模式可选2张
        }

        selectionLauncher.launch(intent)
    }

    private fun showExitDialog() {
        val message = if (networkService.isHost.value) {
            "你是房主，退出将解散房间，确定要退出吗？"
        } else {
            "确定要退出房间吗？"
        }

        AlertDialog.Builder(this)
            .setTitle("退出房间")
            .setMessage(message)
            .setPositiveButton("确定") { _, _ ->
                exitRoom()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun exitRoom() {
        if (hasLeftRoom) return
        hasLeftRoom = true

        if (networkService.isHost.value) {
            networkService.dismissRoom()
        } else {
            networkService.leaveRoom()
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!hasLeftRoom) {
            hasLeftRoom = true
            networkService.leaveRoom()
        }
    }
}

// PlayerAdapter类定义
class PlayerAdapter : RecyclerView.Adapter<PlayerAdapter.PlayerViewHolder>() {
    private var players = listOf<Player>()

    fun updatePlayers(newPlayers: List<Player>) {
        players = newPlayers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): PlayerViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_player, parent, false)
        return PlayerViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        holder.bind(players[position])
    }

    override fun getItemCount() = players.size

    class PlayerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val playerIdText: TextView = view.findViewById(R.id.playerIdText)
        private val playerStatusText: TextView = view.findViewById(R.id.playerStatusText)

        fun bind(player: Player) {
            playerIdText.text = player.id
            playerStatusText.text = when {
                player.isHost -> "【房主】"
                player.isReady -> "【已准备】"
                else -> ""
            }
        }
    }
}

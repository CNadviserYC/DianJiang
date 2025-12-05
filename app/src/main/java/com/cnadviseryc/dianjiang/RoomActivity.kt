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

    private var imageFiles = mutableListOf<File>()
    private var myDrawNumbers = listOf<Int>()
    private var isInDrawing = false
    private var hasLeftRoom = false

    private val selectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        networkService.setPlayerReady()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room)

        playerListView = findViewById(R.id.playerListView)
        countInput = findViewById(R.id.countInput)
        startButton = findViewById(R.id.startButton)
        exitButton = findViewById(R.id.exitButton)
        statusText = findViewById(R.id.statusText)

        val playerId = intent.getStringExtra("playerId") ?: ""

        imageFiles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            @Suppress("UNCHECKED_CAST")
            intent.getSerializableExtra("imageFiles", java.io.Serializable::class.java) as? ArrayList<File>
        } else {
            @Suppress("DEPRECATION", "UNCHECKED_CAST")
            intent.getSerializableExtra("imageFiles") as? ArrayList<File>
        } ?: mutableListOf()

        networkService = NetworkService(this)
        networkService.localPlayerId = playerId

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
                val count = countInput.text.toString().toIntOrNull()
                if (count == null || count <= 0) {
                    Toast.makeText(this, "请输入有效的抽取个数", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (imageFiles.isEmpty()) {
                    Toast.makeText(this, "请先上传将包", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val totalCount = count * networkService.players.value.size
                if (totalCount > imageFiles.size) {
                    Toast.makeText(this, "武将数量不足", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                isInDrawing = true
                startButton.isEnabled = false
                networkService.startDraw(count, imageFiles.size)
            }
        }

        exitButton.setOnClickListener {
            showExitDialog()
        }
    }

    private fun observeNetworkState() {
        lifecycleScope.launch {
            networkService.isHost.collect { isHost ->
                runOnUiThread {
                    countInput.visibility = if (isHost) View.VISIBLE else View.GONE
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
                    is NetworkMessage.RoomDismissed -> {
                        runOnUiThread {
                            Toast.makeText(this@RoomActivity, message.reason, Toast.LENGTH_LONG).show()
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

        val intent = Intent(this, SelectionActivity::class.java)
        intent.putStringArrayListExtra("imagePaths",
            ArrayList(availableImages.map { it.absolutePath }))
        intent.putExtra("isNetworkMode", true)
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
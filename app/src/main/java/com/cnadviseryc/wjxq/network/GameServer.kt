package com.cnadviseryc.wjxq.network

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import com.cnadviseryc.wjxq.R // 导入R类

class GameServer(private val serverPort: Int) : WebSocketServer(InetSocketAddress(serverPort)) {
    private var roomInfo: RoomInfo? = null
    private val playerConnections = mutableMapOf<String, WebSocket>()
    private val allImageResIds = getAllDrawableResourceIds()
    
    var onRoomCreated: ((String) -> Unit)? = null
    var onPlayerJoined: ((PlayerInfo) -> Unit)? = null
    var onPlayerLeft: ((String) -> Unit)? = null
    var onGameStarted: ((GameStartData) -> Unit)? = null
    var onAllSelectionsComplete: (() -> Unit)? = null
    var onPlayerStatusChanged: ((RoomInfo) -> Unit)? = null
    
    override fun onStart() {
        println("服务器启动成功，端口: $serverPort")
    }
    
    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        println("新连接: ${conn.remoteSocketAddress}")
    }
    
    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        val playerId = playerConnections.entries.find { it.value == conn }?.key
        if (playerId != null) {
            playerConnections.remove(playerId)
            removePlayerFromRoom(playerId)
            onPlayerLeft?.invoke(playerId)
        }
    }
    
    override fun onMessage(conn: WebSocket, message: String) {
        try {
            val networkMessage = JsonUtils.fromJson(message, NetworkMessage::class.java)
            handleMessage(conn, networkMessage)
        } catch (_: Exception) {
            sendError(conn, "Invalid message format")
        }
    }
    
    override fun onError(conn: WebSocket?, ex: Exception) {
        ex.printStackTrace()
    }
    
    private fun handleMessage(conn: WebSocket, message: NetworkMessage) {
        when (message.type) {
            MessageType.JOIN_ROOM -> handleJoinRoom(conn, message)
            MessageType.START_GAME -> handleStartGame(message)
            MessageType.SELECTION_COMPLETE -> handleSelectionComplete(message)
            MessageType.PLAYER_STATUS_CHANGED -> handlePlayerStatusChange(message)
            else -> sendError(conn, "Unsupported message type")
        }
    }
    
    private fun handleJoinRoom(conn: WebSocket, message: NetworkMessage) {
        val playerInfo = JsonUtils.fromJson(message.data, PlayerInfo::class.java)
        
        if (roomInfo == null) {
            // 创建房间（房主）
            val roomId = generateRoomId()
            val hostPlayer = playerInfo.copy(isHost = true)
            roomInfo = RoomInfo(
                roomId = roomId,
                hostId = hostPlayer.playerId,
                players = listOf(hostPlayer)
            )
            playerConnections[hostPlayer.playerId] = conn
            
            val response = NetworkMessage(
                type = MessageType.ROOM_CREATED,
                data = JsonUtils.toJson(roomInfo!!),
                roomId = roomId
            )
            conn.send(JsonUtils.toJson(response))
            onRoomCreated?.invoke(roomId)
        } else {
            // 加入房间
            val currentRoom = roomInfo!!
            if (currentRoom.players.size >= 6) {
                sendError(conn, "房间已满")
                return
            }
            
            if (currentRoom.gameStarted) {
                sendError(conn, "游戏已开始")
                return
            }
            
            val updatedPlayers = currentRoom.players + playerInfo
            roomInfo = currentRoom.copy(players = updatedPlayers)
            playerConnections[playerInfo.playerId] = conn
            
            // 通知新玩家房间信息
            val response = NetworkMessage(
                type = MessageType.PLAYER_JOINED,
                data = JsonUtils.toJson(roomInfo!!),
                roomId = currentRoom.roomId
            )
            conn.send(JsonUtils.toJson(response))
            
            // 通知其他玩家有新玩家加入
            broadcastToOthers(playerInfo.playerId, response)
            onPlayerJoined?.invoke(playerInfo)
        }
    }
    
    private fun handleStartGame(message: NetworkMessage) {
        val currentRoom = roomInfo ?: return
        val gameStartData = JsonUtils.fromJson(message.data, GameStartData::class.java)
        
        // 为每个玩家分配不同的图片
        val playerImages = assignImagesToPlayers(currentRoom.players, gameStartData.drawCount)
        val finalGameData = gameStartData.copy(playerImages = playerImages)
        
        roomInfo = currentRoom.copy(
            gameStarted = true,
            drawCount = gameStartData.drawCount,
            selectedImages = emptyMap(),
            allSelectionsComplete = false,
            players = currentRoom.players.map { it.copy(status = PlayerStatus.SELECTING) }
        )
        
        val response = NetworkMessage(
            type = MessageType.GAME_STARTED,
            data = JsonUtils.toJson(finalGameData),
            roomId = currentRoom.roomId
        )
        
        broadcast(response)
        onGameStarted?.invoke(finalGameData)
    }
    
    private fun getAllDrawableResourceIds(): List<Int> {
        return R.drawable::class.java.fields.mapNotNull {
            try {
                // 过滤掉非图片资源
                if (it.name.startsWith("image_")) {
                    it.getInt(null)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private fun handleSelectionComplete(message: NetworkMessage) {
        val currentRoom = roomInfo ?: return
        val selectionData = JsonUtils.fromJson(message.data, SelectionCompleteData::class.java)
        
        val updatedSelections = currentRoom.selectedImages.toMutableMap()
        updatedSelections[selectionData.playerId] = selectionData.selectedImages
        
        val allComplete = updatedSelections.size == currentRoom.players.size
        
        roomInfo = currentRoom.copy(
            selectedImages = updatedSelections,
            allSelectionsComplete = allComplete
        )
        
        if (allComplete) {
            val response = NetworkMessage(
                type = MessageType.ALL_SELECTIONS_COMPLETE,
                data = "",
                roomId = currentRoom.roomId
            )
            broadcast(response)
            onAllSelectionsComplete?.invoke()
        }
    }
    
    fun handlePlayerStatusChange(message: NetworkMessage) {
        val currentRoom = roomInfo ?: return
        val statusChangeData = JsonUtils.fromJson(message.data, PlayerStatusChangeData::class.java)
        
        // 更新玩家状态
        val updatedPlayers = currentRoom.players.map { player ->
            if (player.playerId == statusChangeData.playerId) {
                player.copy(status = statusChangeData.status)
            } else {
                player
            }
        }
        
        roomInfo = currentRoom.copy(players = updatedPlayers)
        
        // 广播状态变更
        val response = NetworkMessage(
            type = MessageType.PLAYER_STATUS_CHANGED,
            data = JsonUtils.toJson(roomInfo!!),
            roomId = currentRoom.roomId
        )
        broadcast(response)
        onPlayerStatusChanged?.invoke(roomInfo!!)
    }
    
    private fun assignImagesToPlayers(players: List<PlayerInfo>, drawCount: Int): Map<String, List<Int>> {
        val totalNeeded = players.size * drawCount
        if (totalNeeded > allImageResIds.size) {
            throw IllegalArgumentException("Not enough images for all players")
        }
        
        val shuffledImages = allImageResIds.shuffled()
        val result = mutableMapOf<String, List<Int>>()
        
        players.forEachIndexed { index, player ->
            val startIndex = index * drawCount
            val endIndex = startIndex + drawCount
            result[player.playerId] = shuffledImages.subList(startIndex, endIndex)
        }
        
        return result
    }
    
    private fun removePlayerFromRoom(playerId: String) {
        val currentRoom = roomInfo ?: return
        val updatedPlayers = currentRoom.players.filter { it.playerId != playerId }
        
        if (updatedPlayers.isEmpty()) {
            roomInfo = null
        } else {
            // 如果离开的是房主，选择新房主
            val newHost = if (currentRoom.hostId == playerId) {
                updatedPlayers.first().playerId
            } else {
                currentRoom.hostId
            }
            
            roomInfo = currentRoom.copy(
                hostId = newHost,
                players = updatedPlayers.map { player ->
                    if (player.playerId == newHost) player.copy(isHost = true)
                    else player.copy(isHost = false)
                }
            )
            
            val response = NetworkMessage(
                type = MessageType.PLAYER_LEFT,
                data = JsonUtils.toJson(roomInfo!!),
                roomId = currentRoom.roomId
            )
            broadcast(response)
        }
    }
    
    private fun generateRoomId(): String {
        // 返回服务器端口作为房间ID
        return serverPort.toString()
    }
    
    private fun broadcast(message: NetworkMessage) {
        val json = JsonUtils.toJson(message)
        playerConnections.values.forEach { conn ->
            try {
                conn.send(json)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun broadcastToOthers(excludePlayerId: String, message: NetworkMessage) {
        val json = JsonUtils.toJson(message)
        playerConnections.entries.forEach { (playerId, conn) ->
            if (playerId != excludePlayerId) {
                try {
                    conn.send(json)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    private fun sendError(conn: WebSocket, errorMessage: String) {
        val response = NetworkMessage(
            type = MessageType.ERROR,
            data = errorMessage
        )
        conn.send(JsonUtils.toJson(response))
    }
    

    
    fun resetGame() {
        val currentRoom = roomInfo ?: return
        roomInfo = currentRoom.copy(
            gameStarted = false,
            drawCount = 0,
            selectedImages = emptyMap(),
            allSelectionsComplete = false,
            players = currentRoom.players.map { it.copy(hasSelectedImages = false) }
        )
        
        val response = NetworkMessage(
            type = MessageType.PLAYER_JOINED,
            data = JsonUtils.toJson(roomInfo!!),
            roomId = currentRoom.roomId
        )
        broadcast(response)
    }
}
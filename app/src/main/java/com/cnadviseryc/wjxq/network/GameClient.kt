package com.cnadviseryc.wjxq.network

import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

class GameClient(serverUri: URI) : WebSocketClient(serverUri) {
    
    var onRoomJoined: ((RoomInfo) -> Unit)? = null
    var onPlayerJoined: ((RoomInfo) -> Unit)? = null
    var onPlayerLeft: ((RoomInfo) -> Unit)? = null
    var onGameStarted: ((GameStartData) -> Unit)? = null
    var onAllSelectionsComplete: (() -> Unit)? = null
    var onPlayerStatusChanged: ((RoomInfo) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    
    override fun onOpen(handshake: ServerHandshake) {
        println("Connected to server")
    }
    
    override fun onMessage(message: String) {
        try {
            val networkMessage = JsonUtils.fromJson(message, NetworkMessage::class.java)
            handleMessage(networkMessage)
        } catch (e: Exception) {
            onError?.invoke("Failed to parse message: ${e.message}")
        }
    }
    
    override fun onClose(code: Int, reason: String, remote: Boolean) {
        println("Connection closed: $reason")
    }
    
    override fun onError(ex: Exception) {
        onError?.invoke("Connection error: ${ex.message}")
    }
    
    private fun handleMessage(message: NetworkMessage) {
        when (message.type) {
            MessageType.PLAYER_JOINED -> {
                val roomInfo = JsonUtils.fromJson(message.data, RoomInfo::class.java)
                onRoomJoined?.invoke(roomInfo)
            }
            MessageType.PLAYER_LEFT -> {
                val roomInfo = JsonUtils.fromJson(message.data, RoomInfo::class.java)
                onPlayerLeft?.invoke(roomInfo)
            }
            MessageType.GAME_STARTED -> {
                val gameStartData = JsonUtils.fromJson(message.data, GameStartData::class.java)
                onGameStarted?.invoke(gameStartData)
            }
            MessageType.ALL_SELECTIONS_COMPLETE -> {
                onAllSelectionsComplete?.invoke()
            }
            MessageType.PLAYER_STATUS_CHANGED -> {
                val roomInfo = JsonUtils.fromJson(message.data, RoomInfo::class.java)
                onPlayerStatusChanged?.invoke(roomInfo)
            }
            MessageType.ERROR -> {
                onError?.invoke(message.data)
            }
            else -> {
                // 忽略其他消息类型
            }
        }
    }
    
    fun joinRoom(playerInfo: PlayerInfo, roomId: String) {
        val message = NetworkMessage(
            type = MessageType.JOIN_ROOM,
            data = JsonUtils.toJson(playerInfo),
            playerId = playerInfo.playerId,
            roomId = roomId
        )
        send(JsonUtils.toJson(message))
    }
    
    fun sendSelectionComplete(playerId: String, selectedImages: List<Int>) {
        val selectionData = SelectionCompleteData(playerId, selectedImages)
        val message = NetworkMessage(
            type = MessageType.SELECTION_COMPLETE,
            data = JsonUtils.toJson(selectionData),
            playerId = playerId
        )
        send(JsonUtils.toJson(message))
    }
}
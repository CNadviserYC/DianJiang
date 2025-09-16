package com.cnadviseryc.wjxq.network

import android.os.Parcelable
import com.google.gson.Gson
import kotlinx.parcelize.Parcelize

// 消息类型枚举
enum class MessageType {
    JOIN_ROOM,
    ROOM_CREATED,
    PLAYER_JOINED,
    PLAYER_LEFT,
    START_GAME,
    GAME_STARTED,
    SELECTION_COMPLETE,
    ALL_SELECTIONS_COMPLETE,
    PLAYER_STATUS_CHANGED,
    ERROR
}

// 玩家状态枚举
enum class PlayerStatus {
    READY,      // 已准备
    SELECTING,  // 选择中
    PLAYING     // 游戏中
}

// 基础消息类
data class NetworkMessage(
    val type: MessageType,
    val data: String = "",
    val playerId: String = "",
    val roomId: String = ""
)

// 房间信息
@Parcelize
data class RoomInfo(
    val roomId: String,
    val hostId: String,
    val players: List<PlayerInfo> = emptyList(),
    val gameStarted: Boolean = false,
    val drawCount: Int = 0,
    val selectedImages: Map<String, List<Int>> = emptyMap(),
    val allSelectionsComplete: Boolean = false
) : Parcelable

// 玩家信息
@Parcelize
data class PlayerInfo(
    val playerId: String,
    val playerName: String,
    val isHost: Boolean = false,
    val hasSelectedImages: Boolean = false,
    val status: PlayerStatus = PlayerStatus.READY
) : Parcelable

// 游戏开始数据
data class GameStartData(
    val drawCount: Int,
    val playerImages: Map<String, List<Int>>
)

// 选择完成数据
data class SelectionCompleteData(
    val playerId: String,
    val selectedImages: List<Int>
)

// 玩家状态变更数据
data class PlayerStatusChangeData(
    val playerId: String,
    val status: PlayerStatus
)

// JSON工具类
object JsonUtils {
    val gson = Gson()
    
    fun toJson(obj: Any): String = gson.toJson(obj)
    
    fun <T> fromJson(json: String, clazz: Class<T>): T = gson.fromJson(json, clazz)
}
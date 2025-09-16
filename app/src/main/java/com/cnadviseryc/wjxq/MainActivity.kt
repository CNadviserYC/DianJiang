package com.cnadviseryc.wjxq

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cnadviseryc.wjxq.network.NetworkManager
import com.cnadviseryc.wjxq.network.RoomInfo
import com.cnadviseryc.wjxq.network.PlayerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {
    private var networkManager: NetworkManager? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 防止息屏
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            DianJiangApp()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 确保在应用退出时释放网络资源
        networkManager?.stopAll()
    }
    
    fun setNetworkManager(manager: NetworkManager) {
        this.networkManager = manager
    }
 }

// 单机游戏界面
@SuppressLint("UnusedBoxWithConstraintsScope", "MutableCollectionMutableState", "DiscouragedApi",
    "DefaultLocale"
)
@Composable
fun SinglePlayerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var count by remember { mutableStateOf("") }
    var randomResIds by remember { mutableStateOf(emptyList<Int>()) }
    val finalSelection = remember { mutableStateListOf<Int>() }
    var showDialog by remember { mutableStateOf(false) }
    var selectionComplete by remember { mutableStateOf(false) }
    var showFullScreen by remember { mutableStateOf(false) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    val maskedStates = remember { mutableStateMapOf<Int, Boolean>() }
    val maskImageResId = remember {
        context.resources.getIdentifier("replace_image", "drawable", context.packageName)
    }



    Box(modifier = Modifier.fillMaxSize()) {
        // 主界面UI
        if (!selectionComplete || showDialog || showFullScreen) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "单机游戏",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = count,
            onValueChange = { count = it },
            label = { Text("抽取数量") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val num = count.toIntOrNull()
                if (num != null && num > 0) {
                    val allImages = (1..638).map { i ->
                        context.resources.getIdentifier("image${String.format("%03d", i)}", "drawable", context.packageName)
                    }.filter { it != 0 }
                    randomResIds = allImages.shuffled().take(num)
                    finalSelection.clear()
                    maskedStates.clear()
                    showDialog = true
                    selectionComplete = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("点将")
        }
            }
        }

        // 全屏图片显示界面
        if (showFullScreen && finalSelection.isNotEmpty()) {
            FullScreenImageDisplay(
                selectedImages = finalSelection,
                onBack = { 
                    showFullScreen = false
                    showDialog = true
                },
                onConfirm = {
                    showFullScreen = false
                    selectionComplete = true
                }
            )
        }

        // 最终图片显示界面 - 完全覆盖屏幕
        if (selectionComplete && finalSelection.isNotEmpty() && !showDialog && !showFullScreen) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                if (finalSelection.size == 1) {
                    val resId = finalSelection[0]
                    val masked = maskedStates[resId] == true
                    val displayResId = if (masked) maskImageResId else resId

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                maskedStates[resId] = !(maskedStates[resId] ?: false)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = displayResId),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                        )
                    }
                } else if (finalSelection.size == 2) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        finalSelection.forEach { resId ->
                            val masked = maskedStates[resId] == true
                            val displayResId = if (masked) maskImageResId else resId

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clickable {
                                        maskedStates[resId] = !(maskedStates[resId] ?: false)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = displayResId),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }

        // 对话框显示
        if (showDialog) {
        if (previewIndex != null) {
            PreviewPage(
                resIds = randomResIds,
                startIndex = previewIndex!!,
                finalSelection = finalSelection,
                onDone = { previewIndex = null }
            )
        } else {
            AlertDialog(
                onDismissRequest = {
                    finalSelection.clear()
                    showDialog = false
                    selectionComplete = false
                },
                title = { Text("请选择1-2张照片") },
                text = {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(100.dp),
                        modifier = Modifier.height(300.dp)
                    ) {
                        itemsIndexed(randomResIds) { idx, resId ->
                            val isSelected = finalSelection.contains(resId)
                            Column(
                                modifier = Modifier.padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    painter = painterResource(id = resId),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clickable {
                                            if (isSelected) finalSelection.remove(resId)
                                            else if (finalSelection.size < 2) finalSelection.add(resId)
                                        },
                                    alpha = if (isSelected) 1f else 0.5f
                                )
                                Text(
                                    text = "预览",
                                    color = Color.Blue,
                                    modifier = Modifier.clickable { previewIndex = idx }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (finalSelection.size in 1..2) {
                            showDialog = false
                            selectionComplete = true
                        }
                    }) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        finalSelection.clear()
                        showDialog = false
                        selectionComplete = false
                    }) {
                        Text("取消")
                    }
                }
            )
        }
    }
    }
}

// 游戏进行界面
@SuppressLint("UnusedBoxWithConstraintsScope", "MutableCollectionMutableState", "DiscouragedApi")
@Composable
fun GamePlayingScreen(
    currentPlayerImages: List<Int>,
    onSelectionComplete: (List<Int>) -> Unit,
    onExitToRoom: () -> Unit,
    onReturnToReady: () -> Unit
) {
    val finalSelection = remember { mutableStateListOf<Int>() }
    var showDialog by remember { mutableStateOf(true) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var showFullScreen by remember { mutableStateOf(false) }
    val maskedStates = remember { mutableStateMapOf<Int, Boolean>() }
    val context = LocalContext.current
    val maskImageResId = remember {
        context.resources.getIdentifier("replace_image", "drawable", context.packageName)
    }



    Box(modifier = Modifier.fillMaxSize()) {
        // 退出按钮
        IconButton(
            onClick = onExitToRoom,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(
                    Color.Black.copy(alpha = 0.3f),
                    CircleShape
                )
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "退出",
                tint = Color.White
            )
        }

        // 全屏图片显示界面
        if (showFullScreen && finalSelection.isNotEmpty()) {
            FullScreenImageDisplay(
                selectedImages = finalSelection,
                onBack = { 
                    showFullScreen = false
                    showDialog = true
                },
                onConfirm = {
                    showFullScreen = false
                    onSelectionComplete(finalSelection.toList())
                }
            )
        }

        // 选择完成后的展示界面
        if (!showDialog && !showFullScreen && finalSelection.isNotEmpty()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                if (finalSelection.size == 1) {
                    val resId = finalSelection[0]
                    val masked = maskedStates[resId] == true
                    val displayResId = if (masked) maskImageResId else resId

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                maskedStates[resId] = !(maskedStates[resId] ?: false)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = displayResId),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                        )
                        
                        // 返回准备按钮
                        IconButton(
                            onClick = onReturnToReady,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.3f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回准备",
                                tint = Color.White
                            )
                        }
                    }
                } else if (finalSelection.size == 2) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            finalSelection.forEach { resId ->
                                val masked = maskedStates[resId] == true
                                val displayResId = if (masked) maskImageResId else resId

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clickable {
                                            maskedStates[resId] = !(maskedStates[resId] ?: false)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = displayResId),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                        
                        // 返回准备按钮
                        IconButton(
                            onClick = onReturnToReady,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.3f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回准备",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    // 选择对话框
    if (showDialog) {
        if (previewIndex != null) {
            PreviewPage(
                resIds = currentPlayerImages,
                startIndex = previewIndex!!,
                finalSelection = finalSelection,
                onDone = { previewIndex = null }
            )
        } else {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("请选择1-2张照片") },
                text = {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(100.dp),
                        modifier = Modifier.height(300.dp)
                    ) {
                        itemsIndexed(currentPlayerImages) { idx, resId ->
                            val isSelected = finalSelection.contains(resId)
                            Column(
                                modifier = Modifier.padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    painter = painterResource(id = resId),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clickable {
                                            if (isSelected) finalSelection.remove(resId)
                                            else if (finalSelection.size < 2) finalSelection.add(resId)
                                        },
                                    alpha = if (isSelected) 0.5f else 1f
                                )
                                Text(
                                    text = "预览",
                                    color = Color.Blue,
                                    modifier = Modifier.clickable { previewIndex = idx }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (finalSelection.size in 1..2) {
                            showDialog = false
                            onSelectionComplete(finalSelection.toList())
                        }
                    }) {
                        Text("确定")
                    }
                },
                dismissButton = null
            )
        }
    }
}

// 全屏图片显示组件
@Composable
fun FullScreenImageDisplay(
    selectedImages: List<Int>,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (selectedImages.size == 1) {
            Image(
                painter = painterResource(id = selectedImages[0]),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else if (selectedImages.size == 2) {
            Column(modifier = Modifier.fillMaxSize()) {
                selectedImages.forEach { resId ->
                    Image(
                        painter = painterResource(id = resId),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
        
        // 返回按钮
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(
                    Color.Black.copy(alpha = 0.3f),
                    CircleShape
                )
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color.White
            )
        }
        
        // 确定按钮
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Text("确定")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PreviewPage(
    resIds: List<Int>,
    startIndex: Int,
    finalSelection: androidx.compose.runtime.snapshots.SnapshotStateList<Int>,
    onDone: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = startIndex) { resIds.size }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val resId = resIds[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = resId),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        IconButton(onClick = onDone, modifier = Modifier.align(Alignment.TopStart)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }

        val currentResId = resIds.getOrNull(pagerState.currentPage)
        if (currentResId != null) {
            val isSelected = finalSelection.contains(currentResId)
            IconButton(
                onClick = {
                    if (isSelected) finalSelection.remove(currentResId)
                    else if (finalSelection.size < 2) finalSelection.add(currentResId)
                },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                val icon = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle
                val tint = if (isSelected) Color.Green else Color.White
                Icon(icon, contentDescription = "选中", tint = tint)
            }
        }

        TextButton(
            onClick = onDone,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Text("确定")
        }
    }
}

// 房间界面
@Composable
fun RoomScreen(
    roomInfo: RoomInfo?,
    isHost: Boolean,
    onStartGame: (Int) -> Unit,
    onLeaveRoom: () -> Unit
) {
    var drawCount by remember { mutableIntStateOf(1) }
    
    // 检查是否所有玩家都已准备
    val allPlayersReady = roomInfo?.players?.all { it.status == PlayerStatus.READY } ?: false
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 顶部信息
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "房间信息",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                roomInfo?.let { info ->
                    Text("房间ID: ${info.roomId}")
                    Text("房主: ${info.players.find { it.isHost }?.playerName ?: ""}")
                    Text("玩家数量: ${info.players.size}")
                }
            }
        }
        
        // 房间成员列表
        Text(
            text = "房间成员",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            roomInfo?.players?.let { players ->
                items(players) { player ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${player.playerName}${if (player.isHost) " (房主)" else ""}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            
                            val statusText = when (player.status) {
                                PlayerStatus.READY -> "已准备"
                                PlayerStatus.SELECTING -> "选择中"
                                PlayerStatus.PLAYING -> "游戏中"
                            }
                            
                            val statusColor = when (player.status) {
                                PlayerStatus.READY -> Color.Green
                                PlayerStatus.SELECTING -> Color(0xFFFFA500) // Orange
                                PlayerStatus.PLAYING -> Color.Blue
                            }
                            
                            Text(
                                text = statusText,
                                color = statusColor,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        
        if (isHost) {
            // 抽取数量选择
            Row(
                modifier = Modifier.padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("抽取数量: ")
                Slider(
                    value = drawCount.toFloat(),
                    onValueChange = { drawCount = it.toInt() },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.weight(1f)
                )
                Text(" $drawCount")
            }
            
            Button(
                onClick = { onStartGame(drawCount) },
                enabled = allPlayersReady && (roomInfo?.players?.size ?: 0) > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(if (allPlayersReady) "开始游戏" else "等待所有玩家准备")
            }
        } else {
            Text(
                text = "等待房主开始游戏...",
                modifier = Modifier.padding(16.dp)
            )
        }
        
        Button(
            onClick = onLeaveRoom,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("离开房间")
        }
    }
}

// 游戏状态枚举
enum class GameState {
    MAIN_MENU,
    SINGLE_PLAYER,
    CREATE_ROOM,
    JOIN_ROOM,
    IN_ROOM,
    GAME_PLAYING
}

@SuppressLint("UnusedBoxWithConstraintsScope", "DiscouragedApi")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DianJiangApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 网络管理器
    val networkManager = remember { NetworkManager(context) }
    
    // 将NetworkManager传递给MainActivity以便在应用退出时释放资源
    LaunchedEffect(networkManager) {
        if (context is MainActivity) {
            context.setNetworkManager(networkManager)
        }
    }
    
    // 游戏状态
    var gameState by remember { mutableStateOf(GameState.MAIN_MENU) }
    
    // 房间相关状态
    var roomInfo by remember { mutableStateOf<RoomInfo?>(null) }
    var currentPlayerImages by remember { mutableStateOf<List<Int>>(emptyList()) }
    var errorMessage by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var isHost by remember { mutableStateOf(false) }
    var playerName by remember { mutableStateOf("") }
    
    // 单机游戏状态 - 移除未使用的属性

    val randomResIds = remember { mutableStateListOf<Int>() }
    val finalSelection = remember { mutableStateListOf<Int>() }
    val maskedStates = remember { mutableStateMapOf<Int, Boolean>() }

    var selectionComplete by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    
    // 网络回调设置
    LaunchedEffect(Unit) {
        networkManager.onRoomCreated = { _ ->
            scope.launch {
                withContext(Dispatchers.Main) {
                    gameState = GameState.IN_ROOM
                    // 房主创建房间时，状态设置为已准备
                    networkManager.changePlayerStatus(PlayerStatus.READY)
                }
            }
        }
        
        networkManager.onRoomJoined = { room ->
            scope.launch {
                withContext(Dispatchers.Main) {
                    roomInfo = room
                    gameState = GameState.IN_ROOM
                    // 玩家加入房间时，状态设置为已准备
                    networkManager.changePlayerStatus(PlayerStatus.READY)
                }
            }
        }
        
        networkManager.onPlayerStatusChanged = { updatedRoom ->
            scope.launch {
                withContext(Dispatchers.Main) {
                    roomInfo = updatedRoom
                }
            }
        }
        
        networkManager.onPlayerJoined = { _ ->
            scope.launch {
                withContext(Dispatchers.Main) {
                    // 更新房间信息，这里需要从服务器获取最新的房间信息
                    // 暂时保持现有逻辑
                }
            }
        }
        
        networkManager.onPlayerLeft = { _ ->
            scope.launch {
                withContext(Dispatchers.Main) {
                    // 更新房间信息，这里需要从服务器获取最新的房间信息
                    // 暂时保持现有逻辑
                }
            }
        }
        
        networkManager.onGameStarted = { gameStartData ->
            scope.launch {
                withContext(Dispatchers.Main) {
                    val playerId = networkManager.getPlayerId()
                    currentPlayerImages = gameStartData.playerImages[playerId] ?: emptyList()
                    randomResIds.clear()
                    randomResIds.addAll(currentPlayerImages)
                    finalSelection.clear()
                    maskedStates.clear()
                    showDialog = true
                    gameState = GameState.GAME_PLAYING
                    // 游戏开始时，玩家状态自动切换为选择中
                    networkManager.changePlayerStatus(PlayerStatus.SELECTING)
                }
            }
        }
        
        networkManager.onAllSelectionsComplete = {
            scope.launch {
                withContext(Dispatchers.Main) {
                    if (networkManager.isHost()) {
                        networkManager.resetGame()
                    }
                }
            }
        }
        
        networkManager.onError = { error ->
            scope.launch {
                withContext(Dispatchers.Main) {
                    errorMessage = error
                    showError = true
                }
            }
        }
    }

    // 返回键处理
    BackHandler {
        when (gameState) {
            GameState.MAIN_MENU -> {
                // 主菜单退出应用
            }
            GameState.SINGLE_PLAYER -> {
                if (selectionComplete && !showDialog) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastBackPressTime < 2000) {
                        showDialog = true
                    } else {
                        lastBackPressTime = now
                        Toast.makeText(context, "再按一次返回退出", Toast.LENGTH_SHORT).show()
                    }
                } else if (previewIndex != null) {
                    previewIndex = null
                } else {
                    gameState = GameState.MAIN_MENU
                }
            }
            GameState.CREATE_ROOM, GameState.JOIN_ROOM -> {
                gameState = GameState.MAIN_MENU
            }
            GameState.IN_ROOM -> {
                networkManager.stopAll()
                gameState = GameState.MAIN_MENU
            }
            GameState.GAME_PLAYING -> {
                if (previewIndex != null) {
                    previewIndex = null
                } else if (selectionComplete && !showDialog) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastBackPressTime < 2000) {
                        showDialog = true
                    } else {
                        lastBackPressTime = now
                        Toast.makeText(context, "再按一次返回退出", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    // 错误提示对话框
    if (showError) {
        AlertDialog(
            onDismissRequest = { showError = false },
            title = { Text("错误") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { showError = false }) {
                    Text("确定")
                }
            }
        )
    }

    // 根据游戏状态显示不同界面
    when (gameState) {
        GameState.MAIN_MENU -> MainMenuScreen(
            onSinglePlayer = { gameState = GameState.SINGLE_PLAYER },
            onCreateRoom = { gameState = GameState.CREATE_ROOM },
            onJoinRoom = { gameState = GameState.JOIN_ROOM }
        )
        
        GameState.SINGLE_PLAYER -> SinglePlayerScreen(
            onBack = { gameState = GameState.MAIN_MENU }
        )
        
        GameState.CREATE_ROOM -> CreateRoomScreen(
            onCreateRoom = { inputPlayerName ->
                scope.launch(Dispatchers.IO) {
                    playerName = inputPlayerName
                    isHost = true
                    val serverIp = networkManager.createRoom(playerName)
                    if (serverIp.isNotEmpty()) {
                        gameState = GameState.IN_ROOM
                    }
                }
            },
            onBack = { gameState = GameState.MAIN_MENU }
        )
        
        GameState.JOIN_ROOM -> JoinRoomScreen(
            onJoinRoom = { inputPlayerName ->
                scope.launch(Dispatchers.IO) {
                    playerName = inputPlayerName
                    isHost = false
                    try {
                        networkManager.joinRoom(playerName)
                        gameState = GameState.IN_ROOM
                    } catch (e: Exception) {
                        // 处理连接失败的情况
                        Log.e("MainActivity", "加入房间失败: ${e.message}")
                        withContext(Dispatchers.Main) {
                            errorMessage = "加入房间失败: ${e.message}"
                            showError = true
                        }
                    }
                }
            },
            onBack = { gameState = GameState.MAIN_MENU }
        )
        
        GameState.IN_ROOM -> RoomScreen(
            roomInfo = roomInfo,
            isHost = isHost,
            onStartGame = { drawCount: Int ->
                networkManager.startGame(drawCount)
            },
            onLeaveRoom = {
                networkManager.stopAll()
                gameState = GameState.MAIN_MENU
            }
        )
        
        GameState.GAME_PLAYING -> GamePlayingScreen(
            currentPlayerImages = randomResIds,
            onSelectionComplete = { selection: List<Int> ->
                // 发送选择完成消息
                networkManager.sendSelectionComplete(selection)
                // 切换玩家状态为游戏中
                networkManager.changePlayerStatus(PlayerStatus.PLAYING)
            },
            onExitToRoom = {
                networkManager.stopAll()
                gameState = GameState.MAIN_MENU
            },
            onReturnToReady = {
                // 切换玩家状态为已准备并返回房间
                networkManager.changePlayerStatus(PlayerStatus.READY)
                gameState = GameState.IN_ROOM
            }
        )
    }
}



// 主菜单界面
@Composable
fun MainMenuScreen(
    onSinglePlayer: () -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "点将",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 48.dp)
        )
        
        Button(
            onClick = onSinglePlayer,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(vertical = 8.dp)
        ) {
            Text("单机游戏", fontSize = 18.sp)
        }
        
        Button(
            onClick = onCreateRoom,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(vertical = 8.dp)
        ) {
            Text("创建房间", fontSize = 18.sp)
        }
        
        Button(
            onClick = onJoinRoom,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(vertical = 8.dp)
        ) {
            Text("加入房间", fontSize = 18.sp)
        }
    }
}

// 创建房间界面
@Composable
fun CreateRoomScreen(
    onCreateRoom: (String) -> Unit,
    onBack: () -> Unit
) {
    var playerName by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "创建房间",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        OutlinedTextField(
            value = playerName,
            onValueChange = { playerName = it },
            label = { Text("输入您的昵称") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                if (playerName.isNotBlank()) {
                    onCreateRoom(playerName)
                }
            },
            enabled = playerName.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("创建房间")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("返回")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "房间将在默认端口8080创建",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

// 加入房间界面
@Composable
fun JoinRoomScreen(
    onJoinRoom: (String) -> Unit,
    onBack: () -> Unit
) {
    var playerName by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "加入房间",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(48.dp))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = playerName,
            onValueChange = { playerName = it },
            label = { Text("输入您的昵称") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                if (playerName.isNotBlank()) {
                    onJoinRoom(playerName)
                }
            },
            enabled = playerName.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("搜索并加入房间")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "将自动搜索局域网中的游戏房间",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}
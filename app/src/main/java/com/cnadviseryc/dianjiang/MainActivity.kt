// MainActivity.kt - 添加了军争模式切换功能
package com.cnadviseryc.dianjiang

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {
    private lateinit var uploadButton: Button
    private lateinit var startButton: Button
    private lateinit var countInput: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    private lateinit var joinRoomButton: Button
    private lateinit var modeSwitch: Button  // 新增：模式切换按钮
    private var imageFiles = mutableListOf<File>()
    private var hasUploadedImages = false
    private var isJunzhengMode = false  // 新增：false = 国战模式, true = 军争模式

    // 新增：SharedPreferences
    private val PREFS_NAME = "DianjiangPrefs"
    private val KEY_IMAGE_PATHS = "imagePaths"

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleZipFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)

        uploadButton = findViewById(R.id.uploadButton)
        startButton = findViewById(R.id.startButton)
        countInput = findViewById(R.id.countInput)
        progressBar = findViewById(R.id.progressBar)
        loadingText = findViewById(R.id.loadingText)
        joinRoomButton = findViewById(R.id.joinRoomButton)
        modeSwitch = findViewById(R.id.modeSwitch)  // 新增

        // 新增：恢复上次保存的图包
        loadSavedImages()

        // 初始状态：禁用开始抽取和联机按钮
        updateButtonStates()

        uploadButton.setOnClickListener {
            filePickerLauncher.launch("application/*")
        }

        startButton.setOnClickListener {
            startExtraction()
        }

        joinRoomButton.setOnClickListener {
            showJoinRoomDialog()
        }

        // 新增：模式切换按钮
        modeSwitch.setOnClickListener {
            isJunzhengMode = !isJunzhengMode
            updateModeSwitch()
        }
        updateModeSwitch()
    }

    // 新增：更新模式切换按钮显示
    private fun updateModeSwitch() {
        if (isJunzhengMode) {
            modeSwitch.text = "当前:军争模式"
            modeSwitch.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
        } else {
            modeSwitch.text = "当前:国战模式"
            modeSwitch.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
        }
    }

    private fun updateButtonStates() {
        startButton.isEnabled = hasUploadedImages
        joinRoomButton.isEnabled = hasUploadedImages

        if (hasUploadedImages) {
            uploadButton.text = "已上传"
            uploadButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        } else {
            uploadButton.text = "上传图包"
            uploadButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        }
    }

    private fun showJoinRoomDialog() {
        val input = EditText(this).apply {
            hint = "请输入你的ID"
            setPadding(50, 30, 50, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("加入房间")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val playerId = input.text.toString().trim()
                if (playerId.isEmpty()) {
                    Toast.makeText(this, "ID不能为空", Toast.LENGTH_SHORT).show()
                } else {
                    checkAndJoinRoom(playerId)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun checkAndJoinRoom(playerId: String) {
        android.util.Log.d("MainActivity", "checkAndJoinRoom 开始，playerId: $playerId")
        android.util.Log.d("MainActivity", "isJunzhengMode: $isJunzhengMode")
        android.util.Log.d("MainActivity", "imageFiles数量: ${imageFiles.size}")

        // 显示等待对话框
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("正在搜索房间...")
            .setMessage("请稍候...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        // 使用 NetworkService 来检测房间
        val tempNetworkService = NetworkService(this)
        tempNetworkService.localPlayerId = playerId

        lifecycleScope.launch {
            // 等待2秒让NSD发现服务
            var roomFound = false
            val startTime = System.currentTimeMillis()

            withContext(Dispatchers.IO) {
                // 启动服务发现
                tempNetworkService.startDiscovery { found ->
                    roomFound = found
                    android.util.Log.d("MainActivity", "房间发现结果: $found")
                }

                // 等待最多3秒
                while (System.currentTimeMillis() - startTime < 3000 && !roomFound) {
                    delay(100)
                }

                // 停止发现
                tempNetworkService.stopDiscovery()
            }

            progressDialog.dismiss()
            android.util.Log.d("MainActivity", "房间搜索完成，roomFound: $roomFound")

            try {
                val intent = Intent(this@MainActivity, RoomActivity::class.java)
                intent.putExtra("playerId", playerId)

                // 修改：传递文件路径而非File对象
                val imagePaths = ArrayList(imageFiles.map { it.absolutePath })
                intent.putStringArrayListExtra("imagePaths", imagePaths)

                intent.putExtra("createRoom", !roomFound)
                intent.putExtra("isJunzhengMode", isJunzhengMode)  // 新增：传递模式信息

                android.util.Log.d("MainActivity", "准备启动RoomActivity")
                startActivity(intent)
                android.util.Log.d("MainActivity", "RoomActivity已启动")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "启动RoomActivity失败", e)
                Toast.makeText(
                    this@MainActivity,
                    "启动失败: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // 以下代码保持原样，包括 handleZipFile, showLoading, getFileName 等方法
    // ... (其余代码与原文件相同)

    private fun startExtraction() {
        val count = countInput.text.toString().toIntOrNull()
        if (count == null || count <= 0) {
            Toast.makeText(this, "请输入有效的抽取个数", Toast.LENGTH_SHORT).show()
            return
        }

        if (count > imageFiles.size) {
            Toast.makeText(this, "输入数字超过可用图片数量", Toast.LENGTH_SHORT).show()
            return
        }

        // 随机抽取指定数量的图片
        val selectedFiles = imageFiles.shuffled().take(count)

        val intent = Intent(this, SelectionActivity::class.java)
        intent.putStringArrayListExtra("imagePaths",
            ArrayList(selectedFiles.map { it.absolutePath }))
        startActivity(intent)
    }

    private fun handleZipFile(uri: Uri) {
        // 显示加载界面
        showLoading(true)

        // 在后台线程处理文件
        lifecycleScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    imageFiles.clear()
                    val tempDir = File(cacheDir, "images").apply {
                        deleteRecursively()
                        mkdirs()
                    }

                    val fileName = getFileName(uri)

                    if (fileName.endsWith(".rar", ignoreCase = true)) {
                        handleRarFile(uri, tempDir)
                    } else {
                        handleZipArchive(uri, tempDir)
                    }

                    imageFiles.sortBy { it.nameWithoutExtension.toIntOrNull() ?: 0 }
                    imageFiles.size
                }

                // 回到主线程显示结果
                showLoading(false)

                if (count > 0) {
                    hasUploadedImages = true
                    updateButtonStates()
                    saveImagePaths()  // 新增：保存图包路径
                    Toast.makeText(this@MainActivity, "已加载 $count 张武将", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "未找到武将图片", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@MainActivity, "文件解析失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            progressBar.visibility = View.VISIBLE
            loadingText.visibility = View.VISIBLE
            uploadButton.isEnabled = false
            startButton.isEnabled = false
            joinRoomButton.isEnabled = false
            modeSwitch.isEnabled = false  // 新增
            countInput.isEnabled = false
        } else {
            progressBar.visibility = View.GONE
            loadingText.visibility = View.GONE
            uploadButton.isEnabled = true
            modeSwitch.isEnabled = true  // 新增
            countInput.isEnabled = true
            updateButtonStates()
        }
    }

    private fun getFileName(uri: Uri): String {
        var result = ""
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    result = cursor.getString(index)
                }
            }
        }
        return result
    }

    private fun handleRarFile(uri: Uri, targetDir: File): Int {
        var count = 0
        val tempRar = File(cacheDir, "temp.rar")

        contentResolver.openInputStream(uri)?.use { input ->
            tempRar.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        Archive(tempRar).use { archive ->
            var fileHeader: FileHeader? = archive.nextFileHeader()
            while (fileHeader != null) {
                if (!fileHeader.isDirectory && isImageFile(fileHeader.fileName)) {
                    val outputFile = File(targetDir, getSimpleFileName(fileHeader.fileName))
                    FileOutputStream(outputFile).use { output ->
                        archive.extractFile(fileHeader, output)
                    }
                    imageFiles.add(outputFile)
                    count++
                }
                fileHeader = archive.nextFileHeader()
            }
        }

        tempRar.delete()
        return count
    }

    private fun handleZipArchive(uri: Uri, targetDir: File): Int {
        var count = 0
        contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && isImageFile(entry.name)) {
                        val fileName = getSimpleFileName(entry.name)
                        val outputFile = File(targetDir, fileName)
                        FileOutputStream(outputFile).use { output ->
                            zis.copyTo(output)
                        }
                        imageFiles.add(outputFile)
                        count++
                    }
                    entry = zis.nextEntry
                }
            }
        }
        return count
    }

    private fun isImageFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    }

    private fun getSimpleFileName(path: String): String {
        return path.substringAfterLast('/')
            .substringAfterLast('\\')
    }

    // 新增：保存图包路径
    private fun saveImagePaths() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val paths = imageFiles.map { it.absolutePath }.toSet()
        prefs.edit().putStringSet(KEY_IMAGE_PATHS, paths).apply()
        android.util.Log.d("MainActivity", "保存了 ${paths.size} 个图片路径")
    }

    // 新增：加载保存的图包
    private fun loadSavedImages() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val paths = prefs.getStringSet(KEY_IMAGE_PATHS, null)

        if (paths != null && paths.isNotEmpty()) {
            imageFiles.clear()
            paths.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    imageFiles.add(file)
                }
            }

            if (imageFiles.isNotEmpty()) {
                imageFiles.sortBy { it.nameWithoutExtension.toIntOrNull() ?: 0 }
                hasUploadedImages = true
                android.util.Log.d("MainActivity", "恢复了 ${imageFiles.size} 张图片")
                Toast.makeText(this, "已自动加载上次的图包 (${imageFiles.size}张)", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

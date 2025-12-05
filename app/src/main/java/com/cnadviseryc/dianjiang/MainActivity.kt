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
    private var imageFiles = mutableListOf<File>()
    private var hasUploadedImages = false

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
                }

                // 等待最多3秒
                while (System.currentTimeMillis() - startTime < 3000 && !roomFound) {
                    delay(100)
                }

                // 停止发现
                tempNetworkService.stopDiscovery()
            }

            progressDialog.dismiss()

            try {
                val intent = Intent(this@MainActivity, RoomActivity::class.java)
                intent.putExtra("playerId", playerId)
                intent.putExtra("imageFiles", ArrayList(imageFiles))
                intent.putExtra("createRoom", !roomFound)

                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "启动失败: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
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
            countInput.isEnabled = false
        } else {
            progressBar.visibility = View.GONE
            loadingText.visibility = View.GONE
            uploadButton.isEnabled = true
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

    private fun handleRarFile(uri: Uri, tempDir: File) {
        val tempRar = File.createTempFile("temp", ".rar", cacheDir)
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempRar).use { output ->
                input.copyTo(output)
            }
        }

        Archive(tempRar).use { archive ->
            var fileHeader: FileHeader? = archive.nextFileHeader()
            while (fileHeader != null) {
                if (!fileHeader.isDirectory && isImageFile(fileHeader.fileName)) {
                    val fileName = File(fileHeader.fileName).name
                    val file = File(tempDir, fileName)
                    FileOutputStream(file).use { output ->
                        archive.extractFile(fileHeader, output)
                    }
                    imageFiles.add(file)
                }
                fileHeader = archive.nextFileHeader()
            }
        }

        tempRar.delete()
    }

    private fun handleZipArchive(uri: Uri, tempDir: File) {
        contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && isImageFile(entry.name)) {
                        val fileName = File(entry.name).name
                        val file = File(tempDir, fileName)
                        FileOutputStream(file).use { output ->
                            zip.copyTo(output)
                        }
                        imageFiles.add(file)
                    }
                    entry = zip.nextEntry
                }
            }
        }
    }

    private fun isImageFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    }

    private fun startExtraction() {
        if (imageFiles.isEmpty()) {
            Toast.makeText(this, "请先上传将包", Toast.LENGTH_SHORT).show()
            return
        }

        val count = countInput.text.toString().toIntOrNull()
        if (count == null || count <= 0) {
            Toast.makeText(this, "请输入有效的抽取个数", Toast.LENGTH_SHORT).show()
            return
        }

        if (count > imageFiles.size) {
            Toast.makeText(this, "抽取个数不能大于武将总数", Toast.LENGTH_SHORT).show()
            return
        }

        val randomIndices = imageFiles.indices.shuffled().take(count)
        val selectedFiles = randomIndices.map { imageFiles[it] }

        val intent = Intent(this, SelectionActivity::class.java)
        intent.putStringArrayListExtra("imagePaths",
            ArrayList(selectedFiles.map { it.absolutePath }))
        startActivity(intent)
    }
}
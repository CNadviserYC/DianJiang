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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import kotlinx.coroutines.Dispatchers
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
    private var imageFiles = mutableListOf<File>()

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

        uploadButton.setOnClickListener {
            filePickerLauncher.launch("application/*")
        }

        startButton.setOnClickListener {
            startExtraction()
        }
    }

    private fun handleZipFile(uri: Uri) {
        showLoading(true)

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

                showLoading(false)
                Toast.makeText(this@MainActivity, "已加载 $count 张图片", Toast.LENGTH_SHORT).show()

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
            countInput.isEnabled = false
        } else {
            progressBar.visibility = View.GONE
            loadingText.visibility = View.GONE
            uploadButton.isEnabled = true
            startButton.isEnabled = true
            countInput.isEnabled = true
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
            Toast.makeText(this, "请先上传图包", Toast.LENGTH_SHORT).show()
            return
        }

        val count = countInput.text.toString().toIntOrNull()
        if (count == null || count <= 0) {
            Toast.makeText(this, "请输入有效的抽取个数", Toast.LENGTH_SHORT).show()
            return
        }

        if (count > imageFiles.size) {
            Toast.makeText(this, "抽取个数不能大于图片总数", Toast.LENGTH_SHORT).show()
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
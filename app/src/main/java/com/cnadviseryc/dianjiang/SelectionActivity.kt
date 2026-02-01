package com.cnadviseryc.dianjiang

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class SelectionActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var displayButton: Button
    private lateinit var titleText: TextView  // 新增
    private val selectedImages = mutableListOf<String>()
    private var imagePaths = arrayListOf<String>()
    private lateinit var adapter: ImageAdapter

    // 新增：军争模式相关变量
    private var isJunzhengMode = false
    private var isNetworkMode = false
    private var playerRole: String? = null
    private var maxSelection = 2 // 默认2张，军争模式为1张

    // 使用新的ActivityResultLauncher替代startActivityForResult
    private val previewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val newSelectedImages = result.data?.getStringArrayListExtra("selectedImages")
            if (newSelectedImages != null) {
                selectedImages.clear()
                selectedImages.addAll(newSelectedImages)
                adapter.notifyDataSetChanged()
            }
        }
    }

    // 新增：用于启动DisplayActivity的launcher
    private val displayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // DisplayActivity关闭后的回调，这里不需要处理
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_selection)

        recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        displayButton = findViewById<Button>(R.id.displayButton)
        titleText = findViewById<TextView>(R.id.titleText)

        imagePaths = intent.getStringArrayListExtra("imagePaths") ?: arrayListOf()

        // 新增：获取军争模式相关参数
        isJunzhengMode = intent.getBooleanExtra("isJunzhengMode", false)
        isNetworkMode = intent.getBooleanExtra("isNetworkMode", false)
        playerRole = intent.getStringExtra("playerRole")
        maxSelection = intent.getIntExtra("maxSelection", 2)

        android.util.Log.d("SelectionActivity", "isJunzhengMode: $isJunzhengMode")
        android.util.Log.d("SelectionActivity", "playerRole: $playerRole")
        android.util.Log.d("SelectionActivity", "maxSelection: $maxSelection")

        // 新增：根据模式和身份设置标题
        if (isJunzhengMode && playerRole != null) {
            // 军争模式：显示身份信息
            val roleText = when (playerRole) {
                "zhugong" -> "主公"
                "fanzei" -> "反贼"
                "neijian" -> "内奸"
                "zhongchen" -> "忠臣"
                else -> "未知"
            }
            titleText.text = "你的身份为$roleText，请选择一位武将"
        } else if (isJunzhengMode) {
            titleText.text = "请选择一位武将"
        } else {
            titleText.text = "请选择两位武将"
        }

        adapter = ImageAdapter(imagePaths)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter

        displayButton.setOnClickListener {
            if (selectedImages.isEmpty()) {
                Toast.makeText(this, "请至少选择一位武将", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, DisplayActivity::class.java)
                intent.putStringArrayListExtra("selectedImages", ArrayList(selectedImages))

                // 新增：传递军争模式参数
                intent.putExtra("isJunzhengMode", isJunzhengMode)
                intent.putExtra("playerRole", playerRole)
                intent.putExtra("isNetworkMode", isNetworkMode)

                // 修改：使用launcher启动，这样需要按两次返回
                displayLauncher.launch(intent)
            }
        }
    }

    inner class ImageAdapter(private val paths: List<String>) :
        RecyclerView.Adapter<ImageAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.imageView)
            val checkBox: CheckBox = view.findViewById(R.id.checkBox)
            val previewButton: Button = view.findViewById(R.id.previewButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_image, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val path = paths[position]

            Glide.with(holder.itemView.context)
                .load(File(path))
                .centerCrop()
                .into(holder.imageView)

            // 重要：先移除监听器，设置状态，再添加监听器，避免触发事件
            holder.checkBox.setOnCheckedChangeListener(null)
            holder.checkBox.isChecked = selectedImages.contains(path)

            // 修改：使用动态的maxSelection
            holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    if (selectedImages.size >= maxSelection && !selectedImages.contains(path)) {
                        // 只有当不在列表中且已达到上限时才拒绝
                        holder.checkBox.isChecked = false

                        // 根据模式显示不同提示
                        val message = if (isJunzhengMode) {
                            "军争模式只能选择一位武将"
                        } else {
                            "国战模式只能选择两位武将"
                        }
                        Toast.makeText(this@SelectionActivity, message, Toast.LENGTH_SHORT).show()
                    } else if (!selectedImages.contains(path)) {
                        selectedImages.add(path)
                    }
                } else {
                    selectedImages.remove(path)
                }
            }

            // 预览按钮点击事件 - 使用新的launcher
            holder.previewButton.setOnClickListener {
                val intent = Intent(this@SelectionActivity, PreviewActivity::class.java)
                intent.putStringArrayListExtra("imagePaths", ArrayList(paths))
                intent.putStringArrayListExtra("selectedImages", ArrayList(selectedImages))
                intent.putExtra("currentPosition", position)
                previewLauncher.launch(intent)
            }
        }

        override fun getItemCount() = paths.size
    }
}

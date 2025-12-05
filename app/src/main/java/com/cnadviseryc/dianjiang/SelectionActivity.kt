package com.cnadviseryc.dianjiang

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
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
    private val selectedImages = mutableListOf<String>()
    private var imagePaths = arrayListOf<String>()
    private lateinit var adapter: ImageAdapter

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_selection)

        recyclerView = findViewById(R.id.recyclerView)
        displayButton = findViewById(R.id.displayButton)

        imagePaths = intent.getStringArrayListExtra("imagePaths") ?: arrayListOf()

        adapter = ImageAdapter(imagePaths)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter

        displayButton.setOnClickListener {
            if (selectedImages.isEmpty()) {
                Toast.makeText(this, "请至少选择一位武将", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, DisplayActivity::class.java)
                intent.putStringArrayListExtra("selectedImages", ArrayList(selectedImages))
                startActivity(intent)
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

            holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    if (selectedImages.size >= 2 && !selectedImages.contains(path)) {
                        // 只有当不在列表中且已达到上限时才拒绝
                        holder.checkBox.isChecked = false
                        Toast.makeText(this@SelectionActivity,
                            "什么模式能选三张武将？", Toast.LENGTH_SHORT).show()
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
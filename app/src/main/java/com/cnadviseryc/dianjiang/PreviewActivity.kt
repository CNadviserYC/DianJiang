package com.cnadviseryc.dianjiang

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class PreviewActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private val selectedImages = mutableListOf<String>()
    private lateinit var imagePaths: ArrayList<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        viewPager = findViewById(R.id.viewPager)

        imagePaths = intent.getStringArrayListExtra("imagePaths") ?: arrayListOf()
        val currentPosition = intent.getIntExtra("currentPosition", 0)
        selectedImages.addAll(intent.getStringArrayListExtra("selectedImages") ?: arrayListOf())

        val adapter = PreviewAdapter(imagePaths, selectedImages)
        viewPager.adapter = adapter
        viewPager.setCurrentItem(currentPosition, false)

        // 使用新的OnBackPressedCallback替代onBackPressed
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val resultIntent = Intent()
                resultIntent.putStringArrayListExtra("selectedImages", ArrayList(selectedImages))
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        })
    }

    inner class PreviewAdapter(
        private val paths: List<String>,
        private val selected: MutableList<String>
    ) : RecyclerView.Adapter<PreviewAdapter.PreviewViewHolder>() {

        inner class PreviewViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.previewImageView)
            val checkBox: CheckBox = view.findViewById(R.id.previewCheckBox)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_preview, parent, false)
            return PreviewViewHolder(view)
        }

        override fun onBindViewHolder(holder: PreviewViewHolder, position: Int) {
            val path = paths[position]

            Glide.with(holder.itemView.context)
                .load(File(path))
                .fitCenter()
                .into(holder.imageView)

            // 重要：先移除监听器，设置状态，再添加监听器
            holder.checkBox.setOnCheckedChangeListener(null)
            holder.checkBox.isChecked = selected.contains(path)

            holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    if (selected.size >= 2 && !selected.contains(path)) {
                        // 只有当不在列表中且已达到上限时才拒绝
                        holder.checkBox.isChecked = false
                        Toast.makeText(this@PreviewActivity,
                            "什么模式能选三张武将？", Toast.LENGTH_SHORT).show()
                    } else if (!selected.contains(path)) {
                        selected.add(path)
                    }
                } else {
                    selected.remove(path)
                }
            }
        }

        override fun getItemCount() = paths.size
    }
}
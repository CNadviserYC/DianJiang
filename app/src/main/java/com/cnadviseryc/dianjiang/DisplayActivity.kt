package com.cnadviseryc.dianjiang

import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import com.bumptech.glide.Glide
import java.io.File

class DisplayActivity : AppCompatActivity() {
    private val imageStates = mutableMapOf<ImageView, Boolean>() // true = 显示替换图片
    private val rotationStates = mutableMapOf<ImageView, Int>() // 0 或 90 度

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display)

        val container: LinearLayout = findViewById(R.id.imageContainer)
        val selectedImages = intent.getStringArrayListExtra("selectedImages") ?: arrayListOf()

        val orientation = resources.configuration.orientation
        container.orientation = if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            LinearLayout.VERTICAL
        } else {
            LinearLayout.HORIZONTAL
        }

        selectedImages.forEach { path ->
            val imageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(4, 4, 4, 4) // 窄边距
            }

            // 加载原始图片
            Glide.with(this)
                .load(File(path))
                .fitCenter()
                .into(imageView)

            // 初始化状态
            imageStates[imageView] = false
            rotationStates[imageView] = 0

            // 设置手势监听器
            val gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
                // 双击切换图片
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    toggleImage(imageView, path)
                    return true
                }

                // 长按旋转图片
                override fun onLongPress(e: MotionEvent) {
                    rotateImage(imageView)
                }
            })

            imageView.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                true
            }

            container.addView(imageView)
        }
    }

    private fun toggleImage(imageView: ImageView, originalPath: String) {
        val isShowingReplace = imageStates[imageView] ?: false

        if (isShowingReplace) {
            // 切换回原始图片
            Glide.with(this)
                .load(File(originalPath))
                .fitCenter()
                .into(imageView)
            imageStates[imageView] = false
        } else {
            // 切换到替换图片
            val replaceImageId = resources.getIdentifier("replace_image", "drawable", packageName)
            if (replaceImageId != 0) {
                Glide.with(this)
                    .load(replaceImageId)
                    .fitCenter()
                    .into(imageView)
                imageStates[imageView] = true
            }
        }
    }

    private fun rotateImage(imageView: ImageView) {
        val currentRotation = rotationStates[imageView] ?: 0
        val newRotation = if (currentRotation == 0) 90 else 0

        // 执行旋转动画
        imageView.animate()
            .rotation(newRotation.toFloat())
            .setDuration(300)
            .start()

        rotationStates[imageView] = newRotation
    }
}
// DisplayActivity.kt - 完整的军争模式支持版本

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
    private var isNetworkMode = false

    // 新增：军争模式变量
    private var isJunzhengMode = false
    private var playerRole: String? = null

    // 新增：双击返回控制
    private var backPressedTime: Long = 0
    private val backPressedInterval: Long = 2000 // 2秒内需要按两次

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display)

        val container: LinearLayout = findViewById(R.id.imageContainer)
        val selectedImages = intent.getStringArrayListExtra("selectedImages") ?: arrayListOf()
        isNetworkMode = intent.getBooleanExtra("isNetworkMode", false)

        // 新增：获取军争模式参数
        isJunzhengMode = intent.getBooleanExtra("isJunzhengMode", false)
        playerRole = intent.getStringExtra("playerRole")

        android.util.Log.d("DisplayActivity", "isJunzhengMode: $isJunzhengMode")
        android.util.Log.d("DisplayActivity", "playerRole: $playerRole")
        android.util.Log.d("DisplayActivity", "selectedImages: ${selectedImages.size}")

        // 新增：拦截返回按钮
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentTime = System.currentTimeMillis()
                if (currentTime - backPressedTime < backPressedInterval) {
                    // 第二次按返回键，真正关闭
                    isEnabled = false // 禁用此回调，让系统处理
                    onBackPressedDispatcher.onBackPressed() // 触发系统返回
                } else {
                    // 第一次按返回键，显示提示
                    backPressedTime = currentTime
                    android.widget.Toast.makeText(
                        this@DisplayActivity,
                        "再按一次返回选择界面",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })

        val orientation = resources.configuration.orientation

        // 军争模式：上下布局，上方显示身份牌，下方显示武将牌
        if (isJunzhengMode && playerRole != null) {
            android.util.Log.d("DisplayActivity", "进入军争模式显示")
            container.orientation = LinearLayout.VERTICAL

            // 添加角色牌(上方) - 占1/2空间
            val roleImageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f  // 权重1
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(8, 8, 8, 8)
            }

            // 加载对应的身份图片
            val roleResId = resources.getIdentifier(playerRole, "drawable", packageName)
            android.util.Log.d("DisplayActivity", "roleResId for $playerRole: $roleResId")
            if (roleResId != 0) {
                Glide.with(this)
                    .load(roleResId)
                    .fitCenter()
                    .into(roleImageView)
                android.util.Log.d("DisplayActivity", "身份牌加载成功")
            } else {
                android.util.Log.e("DisplayActivity", "找不到身份牌资源: $playerRole")
            }

            // 新增：初始化身份牌状态
            imageStates[roleImageView] = false
            rotationStates[roleImageView] = 0

            // 新增：给身份牌添加手势监听
            val roleGestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
                // 双击切换到替换图片2
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    toggleRoleImage(roleImageView)
                    return true
                }

                // 长按旋转图片
                override fun onLongPress(e: MotionEvent) {
                    rotateImage(roleImageView)
                }
            })

            roleImageView.setOnTouchListener { _, event ->
                roleGestureDetector.onTouchEvent(event)
                true
            }

            container.addView(roleImageView)

            // 添加武将牌(下方) - 占1/2空间
            selectedImages.forEach { path ->
                val imageView = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f  // 权重1
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(8, 8, 8, 8)
                }

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
        } else {
            // 国战模式：保持原有逻辑
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
                    setPadding(4, 4, 4, 4)
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

    // 新增：身份牌切换方法（使用replace_image2）
    private fun toggleRoleImage(imageView: ImageView) {
        val isShowingReplace = imageStates[imageView] ?: false

        if (isShowingReplace) {
            // 切换回原始身份牌
            if (playerRole != null) {
                val roleResId = resources.getIdentifier(playerRole, "drawable", packageName)
                if (roleResId != 0) {
                    Glide.with(this)
                        .load(roleResId)
                        .fitCenter()
                        .into(imageView)
                    imageStates[imageView] = false
                }
            }
        } else {
            // 切换到替换图片2
            val replaceImageId = resources.getIdentifier("replace_image2", "drawable", packageName)
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

    override fun finish() {
        // 如果是网络模式,通知房间活动
        if (isNetworkMode) {
            setResult(RESULT_OK)
        }
        super.finish()
    }
}

package com.pet.pet.render.view

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.opengl.GLUtils
import com.live2d.sdk.cubism.framework.CubismFramework
import com.live2d.sdk.cubism.framework.CubismFramework.Option
import com.live2d.sdk.cubism.framework.CubismModelSettingJson
import com.live2d.sdk.cubism.framework.math.CubismMatrix44
import com.live2d.sdk.cubism.framework.model.CubismModel
import com.live2d.sdk.cubism.framework.model.CubismMoc
import com.live2d.sdk.cubism.framework.rendering.android.CubismRendererAndroid
import com.live2d.sdk.cubism.framework.effect.CubismEyeBlink
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import java.io.ByteArrayOutputStream
import kotlin.math.sin

/**
 * 承载 Live2D Cecilia 模型的 View。
 *
 * 目标：先把模型画出来，点击/长按的动作后续再加。
 */
class Live2DPetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), GLSurfaceView.Renderer {

    // Cecilia 的 model3.json 在 assets 下的路径
    private val modelJsonPath = "ceclila_VTS/Cecilia_V4.model3.json"

    private var modelSetting: CubismModelSettingJson? = null
    private var moc: CubismMoc? = null
    private var cubismModel: CubismModel? = null
    private var renderer: CubismRendererAndroid? = null
    private val createdTextureIds = mutableListOf<Int>()
    // 基础矩阵（只包含缩放），每帧在此基础上叠加轻微位移
    private val baseMatrix = CubismMatrix44.create()
    // 每帧使用的 MVP 矩阵
    private val mvpMatrix = CubismMatrix44.create()
    // 眨眼效果
    private var eyeBlink: CubismEyeBlink? = null
    // 时间累计，用于动画
    private var lastFrameTimeNanos: Long = 0L
    private var totalTimeSeconds: Float = 0f

    init {
        // 使用 OpenGL ES 3.0，并启用带 alpha 的配置，让背景透明
        setEGLContextClientVersion(3)
        // RGBA8888 + 16bit depth，无 stencil
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        // 悬浮窗场景下更推荐用 media overlay，避免部分设备重建后不合成/不出画面
        setZOrderMediaOverlay(true)
        // 尽量保留 EGL 上下文（某些情况下能减少重建问题）
        preserveEGLContextOnPause = true

        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            Log.d("Live2DPetView", "onSurfaceCreated()")
            // 每次重建 GL 上下文时，重置计时，避免 deltaTime 异常
            lastFrameTimeNanos = 0L
            totalTimeSeconds = 0f

            initCubismFrameworkIfNeeded()
            // 保险：如果上一次没有释放干净，这里先清理一遍
            releaseResourcesOnGlThread()
            loadModelFromAssets()
            setupRenderer()

            // EGL 上下文每次重建后都必须重新设置 GL 状态
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glDisable(GLES20.GL_CULL_FACE)

            Log.d(
                "Live2DPetView",
                "modelLoaded=${cubismModel != null}, rendererReady=${renderer != null}, textures=${createdTextureIds.size}"
            )
        } catch (e: Exception) {
            Log.e("Live2DPetView", "onSurfaceCreated error: ${e.message}", e)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)

        // 简单按宽高比做个等比缩放 / 居中
        baseMatrix.loadIdentity()
        val aspect = width.toFloat() / height.toFloat()
        // baseScale 控制整体大小，数值越大模型越大
        val baseScale = 2.5f
        if (aspect > 1f) {
            baseMatrix.scale(baseScale / aspect, baseScale)
        } else {
            baseMatrix.scale(baseScale, baseScale * aspect)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        // 清成完全透明，避免出现黑色背景
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val model = cubismModel ?: return
        val r = renderer ?: return

        // 确保每帧 blend 状态正确（部分驱动会在帧间重置）
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // 计算本帧 deltaTime
        val now = System.nanoTime()
        val deltaTimeSeconds = if (lastFrameTimeNanos == 0L) {
            0f
        } else {
            (now - lastFrameTimeNanos) / 1_000_000_000f
        }
        lastFrameTimeNanos = now
        totalTimeSeconds += deltaTimeSeconds

        // 眨眼效果
        eyeBlink?.updateParameters(model, deltaTimeSeconds)

        // 更新模型参数
        model.update()

        // 在基础矩阵上叠加一个轻微的上下浮动，让整体“呼吸”一下
        val bobOffset = (sin(totalTimeSeconds * 2f) * 0.05f).toFloat()
        mvpMatrix.setMatrix(baseMatrix)
        mvpMatrix.translateY(bobOffset)

        // 设置 MVP 矩阵并绘制
        r.setMvpMatrix(mvpMatrix)
        r.drawModel()
    }

    /** 点击动作：后续在这里触发 motion（当前先留空，占位） */
    fun playTapMotion() {
        // TODO: 使用 CubismMotionManager 播放 tap 动作
    }

    /** 待机动作：后续在这里播放 idle motion（当前先留空，占位） */
    fun playIdleMotion() {
        // TODO: 播放 idle 动作
    }

    // 初始化 CubismFramework（只会执行一次）
    private fun initCubismFrameworkIfNeeded() {
        if (!CubismFramework.isStarted()) {
            val option = Option() // 默认不设置 logger，避免过多日志
            CubismFramework.startUp(option)
        }
        if (!CubismFramework.isInitialized()) {
            CubismFramework.initialize()
        }
    }

    // 从 assets 加载 Cecilia 模型
    private fun loadModelFromAssets() {
        // 1. 读 model3.json
        val jsonBytes = context.assets.open(modelJsonPath).use { input ->
            ByteArrayOutputStream().use { out ->
                val buffer = ByteArray(1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        }

        val setting = CubismModelSettingJson(jsonBytes)
        modelSetting = setting

        // 2. 读 moc3
        val mocFileName = setting.modelFileName // 相对路径，例如 "Cecilia_V4.moc3"
        val baseDir = modelJsonPath.substringBeforeLast('/', "")
        val mocPath = if (baseDir.isEmpty()) mocFileName else "$baseDir/$mocFileName"

        val mocBytes = context.assets.open(mocPath).use { input ->
            input.readBytes()
        }

        moc = CubismMoc.create(mocBytes)
        cubismModel = moc?.createModel()

        // 初始化眨眼效果（使用 model3.json 中的 EyeBlink 参数）
        eyeBlink = CubismEyeBlink.create(setting)
    }

    // 创建 Android 渲染器并绑定纹理
    private fun setupRenderer() {
        val model = cubismModel ?: return
        val setting = modelSetting ?: return

        val r = CubismRendererAndroid.create() as CubismRendererAndroid
        r.initialize(model, 1)
        bindModelTextures(r, setting)

        renderer = r
    }

    // 根据 model3.json 中的纹理列表加载并绑定 OpenGL 纹理
    private fun bindModelTextures(
        renderer: CubismRendererAndroid,
        setting: CubismModelSettingJson
    ) {
        val textureCount = setting.textureCount
        if (textureCount <= 0) return

        val baseDir = modelJsonPath.substringBeforeLast('/', "")
        for (i in 0 until textureCount) {
            val texFile = setting.getTextureFileName(i) // 例如 "Cecilia_V4.8192/texture_00.png"
            val fullPath = if (baseDir.isEmpty()) texFile else "$baseDir/$texFile"

            try {
                val bitmap = context.assets.open(fullPath).use { input ->
                    BitmapFactory.decodeStream(input)
                } ?: continue

                val texIds = IntArray(1)
                GLES20.glGenTextures(1, texIds, 0)
                val texId = texIds[0]
                createdTextureIds.add(texId)

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
                GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR
                )
                GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR
                )
                GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE
                )
                GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE
                )

                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
                bitmap.recycle()

                // Live2D 的纹理 index 与模型纹理 index 一一对应
                renderer.bindTexture(i, texId)
            } catch (e: Exception) {
                Log.e("Live2DPetView", "bind texture failed: $fullPath, ${e.message}")
            }
        }
    }

    override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
        // GLSurfaceView 可能会在这里销毁 EGL/GL，上下文丢失前先释放纹理等资源
        try {
            queueEvent {
                releaseResourcesOnGlThread()
            }
        } catch (_: Exception) {
        }
        super.surfaceDestroyed(holder)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 悬浮窗场景没有 Activity 生命周期回调，确保 GLSurfaceView 渲染线程启动
        try {
            onResume()
        } catch (_: Exception) {
        }
    }

    override fun onDetachedFromWindow() {
        // 悬浮窗被移除时先暂停渲染线程，再释放资源，避免下次启动不显示
        try {
            onPause()
        } catch (_: Exception) {
        }

        try {
            queueEvent {
                releaseResourcesOnGlThread()
            }
        } catch (_: Exception) {
        }
        super.onDetachedFromWindow()
    }

    private fun releaseResourcesOnGlThread() {
        // 释放 OpenGL 纹理
        if (createdTextureIds.isNotEmpty()) {
            val arr = createdTextureIds.toIntArray()
            try {
                GLES20.glDeleteTextures(arr.size, arr, 0)
            } catch (_: Exception) {
            }
            createdTextureIds.clear()
        }

        // 释放 renderer（会 close model 引用，但我们下面也会显式释放）
        try {
            renderer?.close()
        } catch (_: Exception) {
        }
        renderer = null

        // 释放模型和 moc
        try {
            val m = cubismModel
            val mocObj = moc
            if (m != null && mocObj != null) {
                mocObj.deleteModel(m)
            } else {
                m?.close()
            }
        } catch (_: Exception) {
        }
        cubismModel = null

        try {
            moc?.delete()
        } catch (_: Exception) {
        }
        moc = null

        modelSetting = null
        eyeBlink = null
    }
}


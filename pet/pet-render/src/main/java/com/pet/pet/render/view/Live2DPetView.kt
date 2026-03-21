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
import org.json.JSONObject
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.sin

class Live2DPetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), GLSurfaceView.Renderer {

    /** 模型来源类型 */
    sealed class ModelSource {
        data class Asset(val modelJsonPath: String) : ModelSource()
        data class External(val modelJsonFile: java.io.File) : ModelSource()
    }

    private var modelSource: ModelSource = ModelSource.Asset("ceclila_VTS/Cecilia_V4.model3.json")
    private var pendingModelSource: ModelSource? = null
    private var modelSetting: CubismModelSettingJson? = null
    private var moc: CubismMoc? = null
    private var cubismModel: CubismModel? = null
    private var renderer: CubismRendererAndroid? = null
    private val createdTextureIds = mutableListOf<Int>()
    private val baseMatrix = CubismMatrix44.create()
    private val mvpMatrix = CubismMatrix44.create()
    private var eyeBlink: CubismEyeBlink? = null
    private var lastFrameTimeNanos: Long = 0L
    private var totalTimeSeconds: Float = 0f
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0

    /** 模型显示大小的参考边长（dp），不随窗口尺寸变化 */
    var modelDisplaySizeDp: Float = 80f

    /** 画布比例回调（当前未使用动态调窗，保留接口） */
    var onCanvasSizeChanged: ((Float, Float) -> Unit)? = null

    // 持久参数：每帧强制设置，不会随时间重置（用于去水印等永久效果）
    // key=paramId, value=Add模式下的增量值
    private val persistentAddParams = mutableMapOf<String, Float>()

    // exp3 表情文件播放状态（持久模式，参数一直保持直到切换新表情）
    private var pendingExpJson: String? = null
    // Triple: (paramId, value, blend) blend: "Add"/"Multiply"/"Overwrite"
    private var activeExpParams: List<Triple<String, Float, String>> = emptyList()
    // 过渡动画：从 fromExpParams 插值到 activeExpParams
    private var fromExpParams: Map<String, Float> = emptyMap()  // 过渡起始值（已解算为最终参数值）
    private var expTransitionElapsed: Float = 0f
    private val expTransitionDuration: Float = 0.3f  // 过渡时长（秒）
    private var expTransitioning: Boolean = false

    // motion3 文件播放状态
    private var pendingMotionJson: String? = null
    private data class MotionKeyframe(val time: Float, val params: List<Pair<String, Float>>)
    private var motionKeyframes: List<MotionKeyframe> = emptyList()
    private var motionFileDuration: Float = 0f
    private var motionFileElapsed: Float = 0f
    private var playingMotionFile: Boolean = false

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderMediaOverlay(true)
        preserveEGLContextOnPause = true
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    // ----------------------------------------------------------------
    // 公开 API
    // ----------------------------------------------------------------

    /** 播放 exp3 表情文件，fileName 为相对模型目录的文件名 */
    fun playExpression(fileName: String) {
        queueEvent {
            try {
                val jsonStr = readFileRelativeToModel(fileName) ?: return@queueEvent
                pendingExpJson = jsonStr
            } catch (e: Exception) {
                Log.e("Live2DPetView", "playExpression failed: $fileName", e)
            }
        }
    }

    /** 清除当前表情，带过渡动画恢复模型默认参数 */
    fun clearExpression() {
        queueEvent {
            // 记录旧表情目标值作为过渡起点
            val fromMap = mutableMapOf<String, Float>()
            for ((paramId, value, _) in activeExpParams) {
                fromMap[paramId] = value
            }
            fromExpParams = fromMap
            activeExpParams = emptyList()
            expTransitionElapsed = 0f
            expTransitioning = fromMap.isNotEmpty()
        }
    }

    /** 播放 motion3 动作文件，fileName 为相对模型目录的文件名 */
    fun playMotionFile(fileName: String) {
        queueEvent {
            try {
                val jsonStr = readFileRelativeToModel(fileName) ?: return@queueEvent
                pendingMotionJson = jsonStr
            } catch (e: Exception) {
                Log.e("Live2DPetView", "playMotionFile failed: $fileName", e)
            }
        }
    }

    /** 切换模型源，在下一帧 GL 线程中重新加载 */
    fun switchModel(source: ModelSource) {
        queueEvent { pendingModelSource = source }
    }

    /**
     * 设置持久参数（Add 模式），每帧强制叠加，永不重置。
     * 用于去水印等需要永久生效的参数调整。
     * @param paramId  Live2D 参数 ID
     * @param addValue 叠加值（对应 exp3 中 Blend=Add 的 Value）
     */
    fun setParamPersistent(paramId: String, addValue: Float) {
        queueEvent { persistentAddParams[paramId] = addValue }
    }

    /** 清除所有持久参数 */
    fun clearPersistentParams() {
        queueEvent { persistentAddParams.clear() }
    }

    // ----------------------------------------------------------------
    // GLSurfaceView.Renderer
    // ----------------------------------------------------------------

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            lastFrameTimeNanos = 0L
            totalTimeSeconds = 0f
            releaseResourcesOnGlThread()
            initCubismFrameworkIfNeeded()
            loadModel(modelSource)
            setupRenderer()
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glDisable(GLES20.GL_CULL_FACE)
            Log.d("Live2DPetView", "onSurfaceCreated model=${cubismModel != null} tex=${createdTextureIds.size}")
        } catch (e: Exception) {
            Log.e("Live2DPetView", "onSurfaceCreated error", e)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        surfaceWidth = width
        surfaceHeight = height
        updateBaseMatrix(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        // 处理模型切换
        pendingModelSource?.let { pending ->
            pendingModelSource = null
            try {
                releaseResourcesOnGlThread()
                initCubismFrameworkIfNeeded()
                modelSource = pending
                loadModel(pending)
                setupRenderer()
                updateBaseMatrix(surfaceWidth, surfaceHeight)
                // 重置表情/动作状态
                activeExpParams = emptyList()
                motionKeyframes = emptyList()
                playingMotionFile = false
            } catch (e: Exception) {
                Log.e("Live2DPetView", "switchModel error", e)
            }
        }

        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val model = cubismModel ?: return
        val r = renderer ?: return
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        val now = System.nanoTime()
        val dt = if (lastFrameTimeNanos == 0L) 0f else (now - lastFrameTimeNanos) / 1_000_000_000f
        lastFrameTimeNanos = now
        totalTimeSeconds += dt

        eyeBlink?.updateParameters(model, dt)

        // 应用 exp3 表情（持久模式 + 过渡动画）
        pendingExpJson?.let { json ->
            pendingExpJson = null
            // 记录旧表情解算后的目标值作为过渡起点
            val fromMap = mutableMapOf<String, Float>()
            for ((paramId, value, blend) in activeExpParams) {
                fromMap[paramId] = value  // 已是解算后目标值
            }
            activeExpParams = parseExpression(json)
            // 新表情参数若旧表情没有，起点为默认值
            for ((paramId, _, _) in activeExpParams) {
                if (!fromMap.containsKey(paramId)) {
                    val id = CubismFramework.getIdManager()?.getId(paramId) ?: continue
                    val idx = try { model.getParameterIndex(id) } catch (_: Exception) { -1 }
                    if (idx >= 0) fromMap[paramId] = model.getParameterDefaultValue(idx)
                }
            }
            fromExpParams = fromMap
            expTransitionElapsed = 0f
            expTransitioning = true
        }

        // 计算各参数的最终目标值（考虑 Blend 类型）
        fun resolveExpTargetValue(model: CubismModel, paramId: String, value: Float, blend: String): Float {
            val id = CubismFramework.getIdManager()?.getId(paramId) ?: return value
            val idx = try { model.getParameterIndex(id) } catch (_: Exception) { return value }
            if (idx < 0) return value
            val defaultVal = model.getParameterDefaultValue(idx)
            return when (blend) {
                "Add"      -> defaultVal + value
                "Multiply" -> defaultVal * value
                else       -> value  // Overwrite
            }
        }

        if (expTransitioning) {
            expTransitionElapsed += dt
            val t = (expTransitionElapsed / expTransitionDuration).coerceIn(0f, 1f)
            val blend = t * t * (3f - 2f * t)
            val allParamIds = (fromExpParams.keys + activeExpParams.map { it.first }).toSet()
            for (paramId in allParamIds) {
                val id = CubismFramework.getIdManager()?.getId(paramId) ?: continue
                val idx = try { model.getParameterIndex(id) } catch (_: Exception) { -1 }
                if (idx < 0) continue
                val fromVal = fromExpParams[paramId] ?: model.getParameterDefaultValue(idx)
                val entry = activeExpParams.firstOrNull { it.first == paramId }
                val toVal = if (entry != null)
                    resolveExpTargetValue(model, entry.first, entry.second, entry.third)
                else
                    model.getParameterDefaultValue(idx)
                model.setParameterValue(idx, fromVal + (toVal - fromVal) * blend)
            }
            if (t >= 1f) expTransitioning = false
        } else {
            // 过渡完成，直接保持目标值
            for ((paramId, value, blendMode) in activeExpParams) {
                val id = CubismFramework.getIdManager()?.getId(paramId) ?: continue
                val idx = try { model.getParameterIndex(id) } catch (_: Exception) { -1 }
                if (idx >= 0) model.setParameterValue(idx, resolveExpTargetValue(model, paramId, value, blendMode))
            }
        }

        // 应用 motion3 关键帧
        pendingMotionJson?.let { json ->
            pendingMotionJson = null
            motionKeyframes = parseMotion3(json)
            motionFileElapsed = 0f
            motionFileDuration = if (motionKeyframes.isNotEmpty()) motionKeyframes.last().time else 0f
            playingMotionFile = motionKeyframes.isNotEmpty()
        }
        if (playingMotionFile) {
            motionFileElapsed += dt
            val elapsed = motionFileElapsed
            val idx = motionKeyframes.indexOfLast { it.time <= elapsed }
            if (idx >= 0) {
                val cur = motionKeyframes[idx]
                val next = motionKeyframes.getOrNull(idx + 1)
                val alpha = if (next != null && next.time > cur.time)
                    (elapsed - cur.time) / (next.time - cur.time) else 1f
                val paramMap = cur.params.toMap().toMutableMap()
                if (next != null && alpha in 0f..1f) {
                    for ((k, v) in next.params) {
                        paramMap[k] = (paramMap[k] ?: v) + (v - (paramMap[k] ?: v)) * alpha
                    }
                }
                for ((paramId, value) in paramMap) {
                    val id = CubismFramework.getIdManager()?.getId(paramId) ?: continue
                    val i = try { model.getParameterIndex(id) } catch (_: Exception) { -1 }
                    if (i >= 0) model.setParameterValue(i, value)
                }
            }
            if (elapsed >= motionFileDuration) playingMotionFile = false
        }

        // 应用持久参数（每帧强制设置为默认值+增量，永不重置）
        for ((paramId, addValue) in persistentAddParams) {
            val id = CubismFramework.getIdManager()?.getId(paramId) ?: continue
            val idx = try { model.getParameterIndex(id) } catch (_: Exception) { -1 }
            if (idx >= 0) {
                val defaultVal = try { model.getParameterDefaultValue(idx) } catch (_: Exception) { 0f }
                model.setParameterValue(idx, defaultVal + addValue)
            }
        }

        model.update()
        val bob = sin(totalTimeSeconds * 2f) * 0.02f
        mvpMatrix.setMatrix(baseMatrix)
        mvpMatrix.translateY(bob)
        r.setMvpMatrix(mvpMatrix)
        r.drawModel()
    }

    // ----------------------------------------------------------------
    // 内部方法
    // ----------------------------------------------------------------

    private fun updateBaseMatrix(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        baseMatrix.loadIdentity()
        val density = context.resources.displayMetrics.density
        val refPx = modelDisplaySizeDp * density
        val s = 2.5f
        val sx = s * refPx / width
        val sy = s * refPx / height
        baseMatrix.scale(sx, sy)
    }

    private fun readFileRelativeToModel(fileName: String): String? {
        return when (val src = modelSource) {
            is ModelSource.Asset -> {
                val baseDir = src.modelJsonPath.substringBeforeLast('/')
                context.assets.open("$baseDir/$fileName").use { it.bufferedReader().readText() }
            }
            is ModelSource.External ->
                java.io.File(src.modelJsonFile.parentFile, fileName).readText()
        }
    }

    private fun initCubismFrameworkIfNeeded() {
        if (!CubismFramework.isStarted()) CubismFramework.startUp(Option())
        if (!CubismFramework.isInitialized()) CubismFramework.initialize()
    }

    private fun loadModel(source: ModelSource) {
        when (source) {
            is ModelSource.Asset    -> loadModelFromAssets(source.modelJsonPath)
            is ModelSource.External -> loadModelFromFile(source.modelJsonFile)
        }
    }

    private fun loadModelFromAssets(modelJsonPath: String) {
        Log.d("Live2DPetView", "loadModelFromAssets: $modelJsonPath")
        val jsonBytes = context.assets.open(modelJsonPath).use { it.readBytes() }
        val setting = CubismModelSettingJson(jsonBytes)
        modelSetting = setting
        val baseDir = modelJsonPath.substringBeforeLast('/')
        val mocPath = "$baseDir/${setting.modelFileName}"
        val mocBytes = context.assets.open(mocPath).use { it.readBytes() }
        moc = CubismMoc.create(mocBytes)
        cubismModel = moc?.createModel()
        eyeBlink = CubismEyeBlink.create(setting)
        Log.d("Live2DPetView", "loadModelFromAssets done: model=${cubismModel != null}")
    }

    private fun loadModelFromFile(modelJsonFile: java.io.File) {
        val jsonBytes = modelJsonFile.readBytes()
        val setting = CubismModelSettingJson(jsonBytes)
        modelSetting = setting
        val baseDir = modelJsonFile.parentFile
        val mocFile = java.io.File(baseDir, setting.modelFileName)
        val mocBytes = mocFile.readBytes()
        moc = CubismMoc.create(mocBytes)
        cubismModel = moc?.createModel()
        eyeBlink = CubismEyeBlink.create(setting)
    }

    private fun setupRenderer() {
        val model = cubismModel ?: return
        val setting = modelSetting ?: return
        val r = CubismRendererAndroid.create() as CubismRendererAndroid
        // 遮罩缓冲区数量按贴图数量动态计算，避免复杂模型遮罩不足导致渲染异常
        val maskBufferCount = ((setting.textureCount + 2) / 3).coerceAtLeast(1)
        r.initialize(model, maskBufferCount)
        bindModelTextures(r, setting)
        renderer = r
        resetModelParameters(model)
        val cwPx = try { model.canvasWidthPixel } catch (_: Exception) { 0f }
        val chPx = try { model.canvasHeightPixel } catch (_: Exception) { 0f }
        val cw   = try { model.canvasWidth } catch (_: Exception) { 0f }
        val ch   = try { model.canvasHeight } catch (_: Exception) { 0f }
        Log.d("Live2DPetView", "canvas pixel:${cwPx}x${chPx} unit:${cw}x${ch}")
        val rw = if (cwPx > 0f) cwPx else cw
        val rh = if (chPx > 0f) chPx else ch
        if (rw > 0f && rh > 0f) post { onCanvasSizeChanged?.invoke(rw, rh) }
    }

    private fun resetModelParameters(model: CubismModel) {
        try {
            val count = model.parameterCount
            for (i in 0 until count) {
                model.setParameterValue(i, model.getParameterDefaultValue(i))
            }
        } catch (e: Exception) {
            Log.e("Live2DPetView", "resetModelParameters failed", e)
        }
    }

    private fun bindModelTextures(renderer: CubismRendererAndroid, setting: CubismModelSettingJson) {
        val source = modelSource
        Log.d("Live2DPetView", "bindModelTextures: source=$source, textureCount=${setting.textureCount}")
        for (i in 0 until setting.textureCount) {
            val texFileName = setting.getTextureFileName(i)
            Log.d("Live2DPetView", "  texture[$i]: $texFileName")
            try {
                val bitmap = when (source) {
                    is ModelSource.Asset -> {
                        val baseDir = source.modelJsonPath.substringBeforeLast('/')
                        val assetPath = "$baseDir/$texFileName"
                        try { context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) } } catch (ex: Exception) { null }
                    }
                    is ModelSource.External -> {
                        val texFile = java.io.File(source.modelJsonFile.parentFile, texFileName)
                        BitmapFactory.decodeFile(texFile.absolutePath)
                    }
                }
                if (bitmap == null) { Log.e("Live2DPetView", "bitmap is null for: $texFileName"); continue }
                val ids = IntArray(1)
                GLES20.glGenTextures(1, ids, 0)
                val texId = ids[0]
                createdTextureIds.add(texId)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
                bitmap.recycle()
                renderer.bindTexture(i, texId)
            } catch (e: Exception) {
                Log.e("Live2DPetView", "bind texture failed[$i]: $texFileName", e)
            }
        }
    }

    private fun parseExpression(json: String): List<Triple<String, Float, String>> {
        return try {
            val obj = JSONObject(json)
            val params = obj.optJSONArray("Parameters") ?: return emptyList()
            (0 until params.length()).mapNotNull { i ->
                val p = params.getJSONObject(i)
                val id = p.optString("Id").ifBlank { null } ?: return@mapNotNull null
                val value = p.optDouble("Value", 0.0).toFloat()
                val blend = p.optString("Blend", "Add")
                // 用特殊标记区分混合模式：Add用正数，Multiply用负数标记，Overwrite用NaN标记
                // 实际在 onDrawFrame 里按 blend 类型处理
                Triple(id, value, blend)
            }
        } catch (e: Exception) {
            Log.e("Live2DPetView", "parseExpression failed", e)
            emptyList()
        }
    }

    private fun parseMotion3(json: String): List<MotionKeyframe> {
        return try {
            val obj = JSONObject(json)
            val meta = obj.optJSONObject("Meta") ?: return emptyList()
            val curves = obj.optJSONArray("Curves") ?: return emptyList()
            val fps = meta.optDouble("Fps", 30.0).toFloat()
            val duration = meta.optDouble("Duration", 0.0).toFloat()
            if (duration <= 0f || fps <= 0f) return emptyList()
            data class CurveData(val id: String, val segments: FloatArray)
            val curveList = mutableListOf<CurveData>()
            for (i in 0 until curves.length()) {
                val c = curves.getJSONObject(i)
                if (c.optString("Target") != "Parameter") continue
                val id = c.optString("Id").ifBlank { null } ?: continue
                val segArr = c.optJSONArray("Segments") ?: continue
                val segs = FloatArray(segArr.length()) { segArr.getDouble(it).toFloat() }
                curveList += CurveData(id, segs)
            }
            if (curveList.isEmpty()) return emptyList()
            val step = 1f / fps
            val frames = mutableListOf<MotionKeyframe>()
            var t = 0f
            while (t <= duration + step * 0.5f) {
                val time = t.coerceAtMost(duration)
                val params = curveList.map { cd -> Pair(cd.id, evalMotionCurve(cd.segments, time)) }
                frames += MotionKeyframe(time, params)
                if (t >= duration) break
                t += step
            }
            frames
        } catch (e: Exception) {
            Log.e("Live2DPetView", "parseMotion3 failed", e)
            emptyList()
        }
    }

    private fun evalMotionCurve(segs: FloatArray, t: Float): Float {
        if (segs.isEmpty()) return 0f
        var i = 0
        var lastVal = 0f
        while (i < segs.size) {
            val segType = segs[i].toInt(); i++
            if (i + 1 >= segs.size) break
            val x0 = segs[i]; val y0 = segs[i + 1]; i += 2
            lastVal = y0
            when (segType) {
                0 -> {
                    if (i + 1 >= segs.size) return y0
                    val x1 = segs[i]; val y1 = segs[i + 1]
                    if (t < x1) return y0 + (y1 - y0) * if (x1 > x0) (t - x0) / (x1 - x0) else 0f
                }
                1 -> {
                    // 贝塞尔段格式：cx1,cy1, cx2,cy2, x1,y1（共6个值）
                    if (i + 5 >= segs.size) return y0
                    val cx1 = segs[i]; val cy1 = segs[i+1]
                    val cx2 = segs[i+2]; val cy2 = segs[i+3]
                    val x1 = segs[i+4]; val y1 = segs[i+5]
                    if (t < x1) {
                        val u = if (x1 > x0) (t - x0) / (x1 - x0) else 0f
                        val iu = 1f - u
                        return iu*iu*iu*y0 + 3f*iu*iu*u*cy1 + 3f*iu*u*u*cy2 + u*u*u*y1
                    }
                    i += 6
                }
                2 -> { if (i + 1 >= segs.size) return y0; if (t < segs[i]) return y0 }
                3 -> { if (i + 1 >= segs.size) return y0; if (t < segs[i]) return segs[i + 1] }
            }
        }
        return lastVal
    }

    override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
        super.surfaceDestroyed(holder)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        try { onResume() } catch (_: Exception) {}
    }

    override fun onDetachedFromWindow() {
        try {
            val latch = CountDownLatch(1)
            queueEvent { releaseResourcesOnGlThread(); latch.countDown() }
            latch.await(500, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {}
        try { onPause() } catch (_: Exception) {}
        super.onDetachedFromWindow()
    }

    private fun releaseResourcesOnGlThread() {
        if (createdTextureIds.isNotEmpty()) {
            try { GLES20.glDeleteTextures(createdTextureIds.size, createdTextureIds.toIntArray(), 0) } catch (_: Exception) {}
            createdTextureIds.clear()
        }
        try { renderer?.close() } catch (_: Exception) {}
        renderer = null
        try {
            val m = cubismModel; val mc = moc
            if (m != null && mc != null) mc.deleteModel(m) else m?.close()
        } catch (_: Exception) {}
        cubismModel = null
        try { moc?.delete() } catch (_: Exception) {}
        moc = null
        modelSetting = null
        eyeBlink = null
        try { if (CubismFramework.isInitialized()) CubismFramework.dispose() } catch (_: Exception) {}
    }
}
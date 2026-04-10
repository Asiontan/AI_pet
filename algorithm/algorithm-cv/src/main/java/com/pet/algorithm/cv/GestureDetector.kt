package com.pet.algorithm.cv

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import com.pet.core.common.logger.PetLogger

/**
 * 手势识别器
 * 基于 MediaPipe Tasks Vision GestureRecognizer 实现
 * 支持：WAVE / THUMBS_UP / FIST / POINT / PEACE
 * 采用 IMAGE 模式（同步调用），每 3 帧处理 1 次降低 CPU 开销
 */
class GestureDetector(private val context: Context) {

    companion object {
        private const val TAG = "GestureDetector"
        /** MediaPipe 手势识别模型文件名，需放于 assets 根目录 */
        private const val MODEL_ASSET = "gesture_recognizer.task"
        /** 最低置信度阈值 */
        private const val MIN_CONFIDENCE = 0.60f
        /** 帧采样间隔：每 3 帧处理 1 次 */
        private const val FRAME_SAMPLE_INTERVAL = 3

        /** MediaPipe 原始标签 → 应用内 Gesture 映射 */
        private val LABEL_MAP = mapOf(
            "Thumb_Up"    to Gesture.THUMBS_UP,
            "Closed_Fist" to Gesture.FIST,
            "Victory"     to Gesture.PEACE,
            "Pointing_Up" to Gesture.POINT,
            "Open_Palm"   to Gesture.WAVE
        )
    }

    private var recognizer: GestureRecognizer? = null
    private var isActive = false
    private var frameCount = 0

    /** 上一次识别到的手势（用于去抖动：连续 2 帧相同才上报） */
    private var pendingGesture: Gesture = Gesture.NONE
    private var pendingCount: Int = 0

    /** 上次触发回调的时间戳，用于冷却防止重复触发（ms） */
    private var lastTriggeredMs: Long = 0L
    private val TRIGGER_COOLDOWN_MS = 2000L  // 2秒内不重复触发同一手势

    /** 手势结果回调，在调用 processFrame 的线程上触发 */
    var onGestureDetected: ((Gesture) -> Unit)? = null

    // ── 生命周期 ──────────────────────────────────────────────────────

    /**
     * 初始化 MediaPipe GestureRecognizer
     * 需要在后台线程调用（IO 操作）
     * @return true 表示初始化成功
     */
    fun initialize(): Boolean {
        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET)
                .build()
            val options = GestureRecognizer.GestureRecognizerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumHands(1)
                .setMinHandDetectionConfidence(MIN_CONFIDENCE)
                .setMinHandPresenceConfidence(MIN_CONFIDENCE)
                .setMinTrackingConfidence(MIN_CONFIDENCE)
                .build()
            recognizer = GestureRecognizer.createFromOptions(context, options)
            PetLogger.d(TAG, "GestureRecognizer initialized successfully")
            true
        } catch (e: Exception) {
            PetLogger.e(TAG, "Failed to initialize GestureRecognizer", e)
            false
        }
    }

    /** 激活手势检测 */
    fun activate() {
        isActive = true
        frameCount = 0
        pendingGesture = Gesture.NONE
        pendingCount = 0
        lastTriggeredMs = 0L
        PetLogger.d(TAG, "Gesture detection activated")
    }

    /** 停用手势检测 */
    fun deactivate() {
        isActive = false
        PetLogger.d(TAG, "Gesture detection deactivated")
    }

    /** 释放 MediaPipe 资源 */
    fun release() {
        isActive = false
        try {
            recognizer?.close()
            recognizer = null
        } catch (e: Exception) {
            PetLogger.e(TAG, "Error releasing recognizer", e)
        }
        PetLogger.d(TAG, "GestureDetector released")
    }

    fun isActivated(): Boolean = isActive

    // ── 帧处理 ────────────────────────────────────────────────────────

    /**
     * 处理摄像头帧，内部进行帧采样与去抖动
     * @param frame 当前帧 Bitmap（需为 ARGB_8888 格式）
     * @return 当前帧识别到的手势（NONE 表示无或已跳过）
     */
    fun processFrame(frame: Bitmap): Gesture {
        if (!isActive || recognizer == null) return Gesture.NONE

        // 帧采样：每 FRAME_SAMPLE_INTERVAL 帧处理 1 次
        frameCount++
        if (frameCount % FRAME_SAMPLE_INTERVAL != 0) return Gesture.NONE

        return try {
            val mpImage = BitmapImageBuilder(frame).build()
            val result: GestureRecognizerResult = recognizer!!.recognize(mpImage)
            val gesture = parseResult(result)
            handleDebounce(gesture)
        } catch (e: Exception) {
            PetLogger.e(TAG, "processFrame error", e)
            Gesture.NONE
        }
    }

    // ── 内部工具 ──────────────────────────────────────────────────────

    /**
     * 解析 MediaPipe 识别结果，取第一只手的最高分手势
     */
    private fun parseResult(result: GestureRecognizerResult): Gesture {
        val gestures = result.gestures()
        if (gestures.isEmpty() || gestures[0].isEmpty()) return Gesture.NONE

        // 打印所有候选手势，方便调试标签名
        val allLabels = gestures[0].joinToString { cat ->
            cat.categoryName() + "=" + String.format("%.2f", cat.score())
        }
        PetLogger.d(TAG, "All candidates: [" + allLabels + "]")

        val topCategory = gestures[0].maxByOrNull { it.score() } ?: return Gesture.NONE
        val label = topCategory.categoryName()
        val score = topCategory.score()

        PetLogger.d(TAG, "Detected: label=$label score=$score")

        if (score < MIN_CONFIDENCE) return Gesture.NONE
        return LABEL_MAP[label] ?: Gesture.NONE
    }

    /**
     * 去抖动：连续 2 帧识别到相同非 NONE 手势才触发回调并返回
     */
    private fun handleDebounce(gesture: Gesture): Gesture {
        if (gesture == Gesture.NONE) {
            pendingGesture = Gesture.NONE
            pendingCount = 0
            return Gesture.NONE
        }
        if (gesture == pendingGesture) {
            pendingCount++
            if (pendingCount == 2) {
                // 冷却判断：2秒内不重复触发
                val now = System.currentTimeMillis()
                if (now - lastTriggeredMs >= TRIGGER_COOLDOWN_MS) {
                    lastTriggeredMs = now
                    onGestureDetected?.invoke(gesture)
                    PetLogger.d(TAG, "Gesture confirmed: $gesture")
                } else {
                    PetLogger.d(TAG, "Gesture cooldown, skipped: $gesture")
                }
                // 重置避免重复触发
                pendingCount = 0
                return gesture
            }
        } else {
            pendingGesture = gesture
            pendingCount = 1
        }
        return Gesture.NONE
    }
}

enum class Gesture {
    NONE,       // 无手势
    WAVE,       // 挥手 / 张开手掌
    THUMBS_UP,  // 点赞
    FIST,       // 握拳
    POINT,      // 指向
    PEACE       // 比耶 / Victory
}

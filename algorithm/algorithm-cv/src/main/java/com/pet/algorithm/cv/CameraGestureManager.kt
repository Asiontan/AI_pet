package com.pet.algorithm.cv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.pet.core.common.logger.PetLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * 摄像头手势管理器
 * 负责 CameraX 生命周期管理、帧捕获与 GestureDetector 调度
 * 使用前置摄像头，分辨率 320x240 以降低功耗
 */
class CameraGestureManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "CameraGestureManager"
        private val TARGET_RESOLUTION = Size(320, 240)
    }

    private val gestureDetector = GestureDetector(context)
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var isRunning = false

    /** 手势识别结果回调，在主线程触发 */
    var onGestureDetected: ((Gesture) -> Unit)? = null

    // ── 生命周期 ──────────────────────────────────────────────────────

    /**
     * 启动摄像头手势识别
     * @param lifecycleOwner 用于绑定 CameraX 生命周期（传入 Service 对应的 LifecycleOwner）
     */
    fun start(lifecycleOwner: LifecycleOwner) {
        if (isRunning) return
        scope.launch(Dispatchers.IO) {
            val ok = gestureDetector.initialize()
            if (!ok) {
                PetLogger.e(TAG, "GestureDetector init failed, camera not started")
                return@launch
            }
            gestureDetector.onGestureDetected = { gesture ->
                // 切回主线程通知上层
                scope.launch(Dispatchers.Main) {
                    onGestureDetected?.invoke(gesture)
                }
            }
            gestureDetector.activate()
            scope.launch(Dispatchers.Main) {
                bindCamera(lifecycleOwner)
            }
        }
    }

    /** 停止摄像头与手势识别 */
    fun stop() {
        if (!isRunning) return
        isRunning = false
        cameraProvider?.unbindAll()
        cameraProvider = null
        gestureDetector.deactivate()
        gestureDetector.release()
        PetLogger.d(TAG, "CameraGestureManager stopped")
    }

    fun isRunning(): Boolean = isRunning

    // ── CameraX 绑定 ──────────────────────────────────────────────────

    private fun bindCamera(lifecycleOwner: LifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(TARGET_RESOLUTION)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            processImageProxy(imageProxy)
                        }
                    }

                cameraProvider!!.unbindAll()
                cameraProvider!!.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    imageAnalysis
                )
                isRunning = true
                PetLogger.d(TAG, "Camera bound successfully")
            } catch (e: Exception) {
                PetLogger.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // ── 帧分析 ────────────────────────────────────────────────────────

    /** 复用的 JPEG 输出缓冲区，避免每帧 new ByteArrayOutputStream */
    private val jpegOutputStream = ByteArrayOutputStream(320 * 240 * 2)

    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxyToBitmap(imageProxy) ?: return
            gestureDetector.processFrame(bitmap)
        } finally {
            imageProxy.close()
        }
    }

    /**
     * 将 ImageProxy（YUV_420_888）手动转换为 ARGB_8888 Bitmap
     * 优化：复用输出缓冲区，批量复制 Y/UV 平面，减少逐像素循环
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val nv21 = yuv420ToNv21(imageProxy)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            jpegOutputStream.reset()
            yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 75, jpegOutputStream)
            val jpegBytes = jpegOutputStream.toByteArray()
            val raw = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                ?: return null
            val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()
            val matrix = Matrix().apply {
                postRotate(rotation)
                postScale(-1f, 1f)
            }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, false)
        } catch (e: Exception) {
            PetLogger.e(TAG, "imageProxyToBitmap failed", e)
            null
        }
    }

    /**
     * YUV_420_888 → NV21 字节数组（优化版）
     * Y 平面：rowStride == width 时直接 bulk copy，否则逐行 bulk copy
     * UV 平面：pixelStride == 2 时直接 bulk copy，否则逐行 bulk copy
     */
    private fun yuv420ToNv21(imageProxy: ImageProxy): ByteArray {
        val width  = imageProxy.width
        val height = imageProxy.height
        val yPlane = imageProxy.planes[0]
        val uPlane = imageProxy.planes[1]
        val vPlane = imageProxy.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val nv21 = ByteArray(width * height * 3 / 2)
        var pos = 0

        // ── Y 平面 ────────────────────────────────────────────────────
        val yRowStride = yPlane.rowStride
        if (yRowStride == width) {
            // 连续内存，一次性批量复制
            yBuffer.rewind()
            yBuffer.get(nv21, 0, width * height)
            pos = width * height
        } else {
            // 有 padding，逐行批量复制
            val rowBuf = ByteArray(width)
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(rowBuf, 0, width)
                System.arraycopy(rowBuf, 0, nv21, pos, width)
                pos += width
            }
        }

        // ── UV 平面（NV21：V 在前，U 在后）────────────────────────────
        val uvRowStride   = vPlane.rowStride
        val uvPixelStride = vPlane.pixelStride
        val uvHeight = height / 2
        val uvWidth  = width / 2

        if (uvPixelStride == 2) {
            // 最常见情况：UV 已经是交错排列，逐行直接复制
            // vBuffer 含 V+U 交错，直接取 vBuffer 的行数据即可得到 NV21 VU 序列
            val rowBuf = ByteArray(uvWidth * 2)
            for (row in 0 until uvHeight) {
                val rowStart = row * uvRowStride
                // 最后一行实际可读字节数可能小于 uvWidth*2（无尾部 padding）
                val available = vBuffer.capacity() - rowStart
                val toCopy = minOf(uvWidth * 2, available)
                if (toCopy <= 0) break
                vBuffer.position(rowStart)
                vBuffer.get(rowBuf, 0, toCopy)
                System.arraycopy(rowBuf, 0, nv21, pos, toCopy)
                // 若最后一行不足，用 0 填充剩余
                if (toCopy < uvWidth * 2) {
                    rowBuf.fill(0, toCopy, uvWidth * 2)
                    System.arraycopy(rowBuf, toCopy, nv21, pos + toCopy, uvWidth * 2 - toCopy)
                }
                pos += uvWidth * 2
            }
        } else {
            // 非交错（pixelStride == 1），逐像素交错写入
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    vBuffer.position(row * uvRowStride + col * uvPixelStride)
                    uBuffer.position(row * uPlane.rowStride + col * uPlane.pixelStride)
                    nv21[pos++] = vBuffer.get()
                    nv21[pos++] = uBuffer.get()
                }
            }
        }
        return nv21
    }
}


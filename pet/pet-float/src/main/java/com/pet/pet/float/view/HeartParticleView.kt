package com.pet.pet.floating.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 双击爱心粒子特效（叠加在 WindowManager 上，持续 900ms 后自动移除）
 * 粒子从宠物中心向四周飞散，带随机旋转和淡出
 */
class HeartParticleView(context: Context) : View(context) {

    private data class Particle(
        var x: Float, var y: Float,
        val vx: Float, val vy: Float,
        val size: Float,
        val color: Int,
        var alpha: Float = 1f,
        var rotation: Float = 0f,
        val rotSpeed: Float
    )

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private var startTime = 0L
    private val duration = 900L

    private val heartColors = intArrayOf(
        0xFFFF6B9D.toInt(),  // 粉红
        0xFFFF4D7D.toInt(),  // 玫瑰红
        0xFFFFB3C6.toInt(),  // 浅粉
        0xFFBBA0FF.toInt(),  // 紫色
        0xFFFF9EBC.toInt()   // 桃粉
    )

    private val tickRunnable = object : Runnable {
        override fun run() {
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < duration) {
                val progress = elapsed.toFloat() / duration
                particles.forEach { p ->
                    p.x += p.vx
                    p.y += p.vy - 0.5f  // 轻微上飘
                    p.alpha = (1f - progress * progress).coerceAtLeast(0f)
                    p.rotation += p.rotSpeed
                }
                invalidate()
                handler.postDelayed(this, 16L)
            } else {
                // 动画结束，通知外部移除
                onAnimationEnd?.invoke()
            }
        }
    }

    var onAnimationEnd: (() -> Unit)? = null

    fun start(centerX: Float, centerY: Float) {
        particles.clear()
        startTime = System.currentTimeMillis()
        val count = 10
        repeat(count) { i ->
            val angle = (Math.PI * 2 * i / count + Random.nextDouble(-0.3, 0.3)).toFloat()
            val speed = Random.nextFloat() * 5f + 3f
            particles.add(Particle(
                x = centerX + Random.nextFloat() * 40 - 20,
                y = centerY + Random.nextFloat() * 40 - 20,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                size = Random.nextFloat() * 18f + 12f,
                color = heartColors[Random.nextInt(heartColors.size)],
                rotSpeed = Random.nextFloat() * 8f - 4f
            ))
        }
        handler.post(tickRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        particles.forEach { p ->
            paint.color = p.color
            paint.alpha = (p.alpha * 255).toInt()
            canvas.save()
            canvas.rotate(p.rotation, p.x, p.y)
            drawHeart(canvas, p.x, p.y, p.size)
            canvas.restore()
        }
    }

    private fun drawHeart(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        // 用两个圆 + 菱形近似爱心
        val r = size * 0.35f
        canvas.drawCircle(cx - r, cy - r * 0.2f, r, paint)
        canvas.drawCircle(cx + r, cy - r * 0.2f, r, paint)
        // 下三角
        val path = android.graphics.Path().apply {
            moveTo(cx - size * 0.72f, cy - r * 0.2f)
            lineTo(cx + size * 0.72f, cy - r * 0.2f)
            lineTo(cx, cy + size * 0.72f)
            close()
        }
        canvas.drawPath(path, paint)
    }

    companion object {
        /**
         * 在指定宠物悬浮窗坐标处播放爱心粒子特效
         * @param petX  宠物 WindowManager x
         * @param petY  宠物 WindowManager y
         * @param petSize 宠物尺寸 px
         */
        fun playAt(context: Context, petX: Int, petY: Int, petSize: Int) {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = HeartParticleView(context)
            val size = petSize + 200  // 特效区域稍大于宠物
            val lp = WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = petX - 100
                y = petY - 100
            }
            try {
                wm.addView(view, lp)
                view.onAnimationEnd = {
                    try { wm.removeView(view) } catch (_: Exception) {}
                }
                view.start(size / 2f, size / 2f)
            } catch (e: Exception) {
                android.util.Log.e("HeartParticleView", "playAt failed", e)
            }
        }
    }
}


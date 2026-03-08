package com.pet.core.common.util

import android.content.Context
import android.graphics.Point
import android.util.DisplayMetrics
import android.view.WindowManager

object DensityUtils {
    fun dp2px(context: Context, dp: Float): Int {
        val scale = getDisplayMetrics(context).density
        return (dp * scale + 0.5f).toInt()
    }

    fun getScreenWidth(context: Context): Int {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val point = Point()
        windowManager.defaultDisplay.getRealSize(point)
        return point.x
    }

    fun getScreenHeight(context: Context): Int {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val point = Point()
        windowManager.defaultDisplay.getRealSize(point)
        return point.y
    }

    fun getStatusBarHeight(context: Context): Int {
        return dp2px(context, 25f)
    }

    private fun getDisplayMetrics(context: Context): DisplayMetrics {
        return context.resources.displayMetrics
    }
}

package com.pet.core.common.util

import android.os.Handler
import android.os.Looper

object ThreadUtils {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun runOnUiThread(action: () -> Unit) {
        if (isMainThread()) {
            action.invoke()
        } else {
            mainHandler.post(action)
        }
    }

    fun isMainThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }
}

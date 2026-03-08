package com.pet.core.common.logger

import android.util.Log

object PetLogger {
    private const val TAG = "PetDesktop"
    private var isDebug = true

    fun setDebugMode(debug: Boolean) {
        isDebug = debug
    }

    fun d(tag: String?, message: String) {
        if (isDebug) Log.d(tag ?: TAG, message)
    }

    fun i(tag: String?, message: String) {
        if (isDebug) Log.i(tag ?: TAG, message)
    }

    fun w(tag: String?, message: String) {
        if (isDebug) Log.w(tag ?: TAG, message)
    }

    fun w(tag: String?, message: String, throwable: Throwable) {
        if (isDebug) Log.w(tag ?: TAG, message, throwable)
    }

    fun e(tag: String?, message: String) {
        if (isDebug) Log.e(tag ?: TAG, message)
    }

    fun e(tag: String?, message: String, throwable: Throwable) {
        if (isDebug) Log.e(tag ?: TAG, message, throwable)
    }
}

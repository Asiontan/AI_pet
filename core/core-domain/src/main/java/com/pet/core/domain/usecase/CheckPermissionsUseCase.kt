package com.pet.core.domain.usecase

import android.content.Context
import com.pet.core.common.result.Result

class CheckPermissionsUseCase(private val context: Context) {
    fun checkOverlayPermission(): Result<Boolean> = try {
        val granted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(context)
        } else {
            true
        }
        Result.Success(granted)
    } catch (e: Exception) {
        Result.Error(e)
    }

    fun checkNotificationPermission(): Result<Boolean> = try {
        Result.Success(true)
    } catch (e: Exception) {
        Result.Error(e)
    }
}

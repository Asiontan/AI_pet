package com.pet.pet.service.manager

import android.content.Context
import android.content.Intent
import com.pet.pet.service.PetForegroundService
import com.pet.core.common.logger.PetLogger

/**
 * 宠物服务管理器
 */
object PetServiceManager {
    
    /**
     * 启动服务
     */
    fun startService(context: Context) {
        try {
            val intent = Intent(context, PetForegroundService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            PetLogger.d("PetServiceManager", "Service started")
        } catch (e: Exception) {
            PetLogger.e("PetServiceManager", "Failed to start service", e)
        }
    }
    
    /**
     * 停止服务
     */
    fun stopService(context: Context) {
        try {
            val intent = Intent(context, PetForegroundService::class.java)
            context.stopService(intent)
            PetLogger.d("PetServiceManager", "Service stopped")
        } catch (e: Exception) {
            PetLogger.e("PetServiceManager", "Failed to stop service", e)
        }
    }
}


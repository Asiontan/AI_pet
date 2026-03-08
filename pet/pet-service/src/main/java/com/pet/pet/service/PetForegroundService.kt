package com.pet.pet.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.R as AndroidR
import com.pet.core.common.logger.PetLogger
import com.pet.core.domain.model.event.UserInteractionEvent
import com.pet.pet.floating.manager.PetFloatManager
import com.pet.pet.service.coordinator.ServiceLifecycleCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 宠物前台服务
 * 整合所有模块，管理宠物生命周期
 */
class PetForegroundService : Service() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var floatManager: PetFloatManager
    private lateinit var lifecycleCoordinator: ServiceLifecycleCoordinator
    
    override fun onCreate() {
        super.onCreate()
        PetLogger.d("PetForegroundService", "Service created")
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        floatManager = PetFloatManager(this)
        lifecycleCoordinator = ServiceLifecycleCoordinator(this, serviceScope)

        // 将悬浮宠物的交互事件转发给行为协调器
        floatManager.setInteractionHandler { interaction: UserInteractionEvent ->
            lifecycleCoordinator.handleUserInteraction(interaction)
        }

        // 启动宠物
        serviceScope.launch {
            lifecycleCoordinator.start()
            floatManager.show()
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        PetLogger.d("PetForegroundService", "Service started")
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        PetLogger.d("PetForegroundService", "Service destroyed")
        
        serviceScope.launch {
            lifecycleCoordinator.stop()
            floatManager.hide()
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pet Desktop Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Pet Desktop foreground service"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent().apply {
                setClassName(packageName, "com.example.pet.MainActivity")
            }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pet Desktop")
            .setContentText("宠物正在运行")
            .setSmallIcon(AndroidR.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()
    }
    
    companion object {
        private const val CHANNEL_ID = "pet_service_channel"
        private const val NOTIFICATION_ID = 1
    }
}


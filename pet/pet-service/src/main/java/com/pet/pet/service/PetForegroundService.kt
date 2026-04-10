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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import android.R as AndroidR
import com.pet.core.common.logger.PetLogger
import com.pet.core.common.result.Result
import com.pet.core.data.preferences.PetPreferences
import com.pet.core.data.repository.PetRepository
import com.pet.core.domain.model.PetPosition
import com.pet.core.domain.model.event.UserInteractionEvent
import com.pet.pet.floating.manager.PetFloatManager
import com.pet.pet.render.view.Live2DPetView
import com.pet.pet.service.chat.ChatManager
import com.pet.pet.service.coordinator.ServiceLifecycleCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/**
 * 宠物前台服务
 * 整合所有模块，管理宠物生命周期
 */
class PetForegroundService : Service(), LifecycleOwner {

    private val dispatcher = ServiceLifecycleDispatcher(this)
    override val lifecycle get() = dispatcher.lifecycle

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var floatManager: PetFloatManager
    private lateinit var lifecycleCoordinator: ServiceLifecycleCoordinator
    private lateinit var repository: PetRepository
    private lateinit var chatManager: ChatManager
    private var startedOnce: Boolean = false

    override fun onCreate() {
        dispatcher.onServicePreSuperOnCreate()
        super.onCreate()
        PetLogger.d("PetForegroundService", "Service created")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        floatManager = PetFloatManager(this)
        lifecycleCoordinator = ServiceLifecycleCoordinator(this, serviceScope)
        lifecycleCoordinator.floatManager = floatManager
        lifecycleCoordinator.onOpenChat = { openChatDialog() }
        lifecycleCoordinator.onSwipeDown = {
            // 发广播给 app 层，由 app 层调用 PetAccessibilityService 执行下滑
            sendBroadcast(Intent(ACTION_SWIPE_DOWN).apply {
                setPackage(packageName)
            })
        }
        repository = PetRepository(PetPreferences(this))
        // 复用 Application 单例，与 ChatDialogActivity 共享同一个 ChatManager（历史记录一致）
        chatManager = ChatManager(this)
        lifecycleCoordinator.chatManager = chatManager

        floatManager.setInteractionHandler { interaction: UserInteractionEvent ->
            if (interaction.type == com.pet.core.domain.model.event.InteractionType.LONG_PRESS) {
                // 长按宠物打开聊天界面
                openChatDialog()
            } else {
            lifecycleCoordinator.handleUserInteraction(interaction)
            }
        }

        floatManager.setPositionSettledListener { x, y ->
            serviceScope.launch {
                try {
                    repository.savePetPosition(PetPosition(x = x, y = y))
                } catch (e: Exception) {
                    PetLogger.e("PetForegroundService", "Failed to save pet position", e)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        dispatcher.onServicePreSuperOnStart()
        PetLogger.d("PetForegroundService", "Service onStartCommand action=${intent?.action}")
        ensureStartedAndShown()
        // 处理来自 ModelSwitchActivity 的模型/表情/动作指令
        when (intent?.action) {
            ACTION_SWITCH_MODEL -> {
                val modelJsonPath = intent.getStringExtra(EXTRA_MODEL_JSON_PATH)
                    ?: return START_STICKY
                val isExternal = intent.getBooleanExtra(EXTRA_IS_EXTERNAL, false)
                val source = if (isExternal)
                    Live2DPetView.ModelSource.External(java.io.File(modelJsonPath))
                else
                    Live2DPetView.ModelSource.Asset(modelJsonPath)
                floatManager.switchModel(source)
            }
            ACTION_PLAY_EXPRESSION -> {
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME)
                    ?: return START_STICKY
                floatManager.playExpression(fileName)
            }
            ACTION_PLAY_MOTION -> {
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME)
                    ?: return START_STICKY
                floatManager.playMotionFile(fileName)
            }
            ACTION_OPEN_CHAT -> openChatDialog()
            ACTION_APPLY_EMOTION -> {
                val score = intent.getIntExtra(EXTRA_EMOTION_SCORE, 5)
                lifecycleCoordinator.applyEmotionFromChat(score)
            }
            ACTION_START_GESTURE -> {
                lifecycleCoordinator.startGestureRecognition(this)
                PetLogger.d("PetForegroundService", "Gesture recognition started via intent")
            }
            ACTION_STOP_GESTURE -> {
                lifecycleCoordinator.stopGestureRecognition()
                PetLogger.d("PetForegroundService", "Gesture recognition stopped via intent")
            }
        }
        return START_STICKY
    }

    private fun openChatDialog() {
        try {
            // 先广播通知 app 层关闭 Mini 悬浮条（若存在），再打开全屏聊天
            // app 层的 ChatMiniService 会监听此广播并自行关闭
            sendBroadcast(Intent(ACTION_CLOSE_MINI_AND_OPEN_CHAT).apply {
                setPackage(packageName)
            })
        } catch (e: Exception) {
            PetLogger.e("PetForegroundService", "Failed to open chat dialog", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        dispatcher.onServicePreSuperOnDestroy()
        super.onDestroy()
        PetLogger.d("PetForegroundService", "Service destroyed")
        try { lifecycleCoordinator.stop() } catch (e: Exception) {
            PetLogger.e("PetForegroundService", "Failed to stop coordinator", e)
        }
        try { floatManager.hide() } catch (e: Exception) {
            PetLogger.e("PetForegroundService", "Failed to hide float view", e)
        }
        // 通知 app 层关闭对话页面和 Mini 悬浮条
        try {
            sendBroadcast(Intent(ACTION_CLOSE_ALL_CHAT_UI).apply {
                setPackage(packageName)
            })
        } catch (_: Exception) {}
        startedOnce = false
    }

    private fun ensureStartedAndShown() {
        if (startedOnce) {
            if (!floatManager.isShowing()) floatManager.show()
            return
        }
        startedOnce = true
        try { lifecycleCoordinator.start() } catch (e: Exception) {
            PetLogger.e("PetForegroundService", "Failed to start coordinator", e)
        }
        // 恢复手势识别开关的持久化状态
        try {
            val prefs = com.pet.core.data.preferences.PetPreferences(this)
            if (prefs.isGestureRecognitionEnabled()) {
                lifecycleCoordinator.startGestureRecognition(this)
                PetLogger.d("PetForegroundService", "Gesture recognition auto-restored")
            }
        } catch (e: Exception) {
            PetLogger.e("PetForegroundService", "Failed to restore gesture recognition", e)
        }
        try { floatManager.show() } catch (e: Exception) {
            PetLogger.e("PetForegroundService", "Failed to show float view", e)
        }
        serviceScope.launch {
            try {
                val posResult = withContext(Dispatchers.IO) { repository.getPetPosition() }
                if (posResult is Result.Success) floatManager.updatePosition(posResult.data)
            } catch (e: Exception) {
                PetLogger.e("PetForegroundService", "Failed to restore pet position", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pet Desktop Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Pet Desktop foreground service" }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent().apply { setClassName(packageName, "com.example.pet.MainActivity") }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
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

        const val ACTION_SWITCH_MODEL    = "com.pet.action.SWITCH_MODEL"
        const val ACTION_PLAY_EXPRESSION = "com.pet.action.PLAY_EXPRESSION"
        const val ACTION_PLAY_MOTION     = "com.pet.action.PLAY_MOTION"
        const val ACTION_OPEN_CHAT       = "com.pet.action.OPEN_CHAT"
        const val ACTION_APPLY_EMOTION   = "com.pet.action.APPLY_EMOTION"
        const val ACTION_START_GESTURE   = "com.pet.action.START_GESTURE"
        const val ACTION_STOP_GESTURE    = "com.pet.action.STOP_GESTURE"
        const val ACTION_SWIPE_DOWN      = "com.pet.action.SWIPE_DOWN"
        /** app 层监听此广播：先关闭 Mini 条，再打开全屏聊天 */
        const val ACTION_CLOSE_MINI_AND_OPEN_CHAT = "com.pet.action.CLOSE_MINI_AND_OPEN_CHAT"
        /** app 层监听此广播：关闭所有聊天 UI（服务停止时发送） */
        const val ACTION_CLOSE_ALL_CHAT_UI = "com.pet.action.CLOSE_ALL_CHAT_UI"

        const val EXTRA_MODEL_JSON_PATH  = "model_json_path"
        const val EXTRA_IS_EXTERNAL      = "is_external"
        const val EXTRA_FILE_NAME        = "file_name"
        const val EXTRA_EMOTION_SCORE    = "emotion_score"

        fun cmdSwitchModel(context: Context, modelJsonPath: String, isExternal: Boolean) {
            context.startService(
                Intent(context, PetForegroundService::class.java).apply {
                    action = ACTION_SWITCH_MODEL
                    putExtra(EXTRA_MODEL_JSON_PATH, modelJsonPath)
                    putExtra(EXTRA_IS_EXTERNAL, isExternal)
                }
            )
        }

        fun cmdPlayExpression(context: Context, fileName: String) {
            context.startService(
                Intent(context, PetForegroundService::class.java).apply {
                    action = ACTION_PLAY_EXPRESSION
                    putExtra(EXTRA_FILE_NAME, fileName)
                }
            )
        }

        fun cmdPlayMotion(context: Context, fileName: String) {
            context.startService(
                Intent(context, PetForegroundService::class.java).apply {
                    action = ACTION_PLAY_MOTION
                    putExtra(EXTRA_FILE_NAME, fileName)
                }
            )
        }

        /** 开启摄像头手势识别 */
        fun cmdStartGesture(context: Context) {
            context.startService(
                Intent(context, PetForegroundService::class.java).apply {
                    action = ACTION_START_GESTURE
                }
            )
        }

        /** 关闭摄像头手势识别 */
        fun cmdStopGesture(context: Context) {
            context.startService(
                Intent(context, PetForegroundService::class.java).apply {
                    action = ACTION_STOP_GESTURE
                }
            )
        }
    }
}

package com.pet.pet.service.coordinator

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleOwner
import com.pet.algorithm.cv.CameraGestureManager
import com.pet.algorithm.cv.Gesture
import com.pet.algorithm.prediction.BehaviorPredictor
import com.pet.algorithm.rl.RLBehaviorManager
import com.pet.algorithm.sentiment.BehaviorEmotionAnalyzer
import com.pet.algorithm.sentiment.TextSentimentAnalyzer
import com.pet.core.common.logger.PetLogger
import com.pet.core.data.model.ModelManager
import com.pet.core.data.preferences.PetPreferences
import com.pet.core.domain.model.event.InteractionType
import com.pet.core.domain.model.event.UserInteractionEvent
import com.pet.pet.behavior.statemachine.PetBehaviorStateMachine
import com.pet.pet.floating.manager.PetFloatManager
import com.pet.pet.service.chat.ChatManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 服务生命周期协调器
 * 新增功能：
 * - 亲密度系统：每次交互 +1，聊天完成 +2，每小时闲置超8小时 -1
 * - 在线时长统计：停止服务时累加
 * - 关心通知：超过2小时未交互推送通知（每3小时冷却）
 * - 气泡进场/退场动画、打字机光标闪烁（已在 PetBubbleView 实现）
 */
class ServiceLifecycleCoordinator(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "ServiceLifecycleCoordinator"
        private const val CARE_NOTIFY_CHANNEL    = "pet_care_channel"
        private const val CARE_NOTIFY_ID         = 2001
        private const val CARE_IDLE_THRESHOLD_MS  = 2 * 60 * 60 * 1000L  // 2小时
        private const val CARE_NOTIFY_COOLDOWN_MS = 3 * 60 * 60 * 1000L  // 3小时冷却
        private const val BOND_DECAY_IDLE_MS      = 8 * 60 * 60 * 1000L  // 8小时触发衰减
    }

    private lateinit var rlBehaviorManager: RLBehaviorManager
    private lateinit var behaviorStateMachine: PetBehaviorStateMachine
    private lateinit var textSentimentAnalyzer: TextSentimentAnalyzer
    private lateinit var behaviorEmotionAnalyzer: BehaviorEmotionAnalyzer
    private lateinit var behaviorPredictor: BehaviorPredictor
    private lateinit var preferences: PetPreferences

    var floatManager: PetFloatManager? = null
    var chatManager: ChatManager? = null
    /** 打开聊天界面的回调，由 PetForegroundService 注入 */
    var onOpenChat: (() -> Unit)? = null
    /** 执行屏幕下滑的回调，由 PetForegroundService 注入 */
    var onSwipeDown: (() -> Unit)? = null

    /** 手势识别管理器，调用 startGestureRecognition() 后启动 */
    private var cameraGestureManager: CameraGestureManager? = null

    private var isRunning = false
    private var serviceStartTimeMs = 0L
    private var lastUserInteractionMs = System.currentTimeMillis()

    private val careMessages = listOf(
        "好久不见啦，你在做什么呢？(｡•ᴗ•｡)",
        "主人，记得休息一下哦~",
        "我想你了！快来陪我玩吧！",
        "喝水了吗？记得保持水分哦！",
        "今天辛苦了，给你一个虚拟拥抱 ♡",
        "你不来找我，我可要来找你了！",
        "主人有没有好好吃饭呀？",
        "外面天气怎么样？我只能从屏幕看世界..."
    )

    // 气泡 BroadcastReceiver
    private val bubbleReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                ChatManager.ACTION_BUBBLE_TOKEN -> {
                    val token = intent.getStringExtra(ChatManager.EXTRA_TOKEN) ?: return
                    val fm = floatManager ?: return
                    if (!fm.isBubbleShowing()) fm.showBubble()
                    fm.appendBubbleToken(token)
                }
                ChatManager.ACTION_BUBBLE_DONE -> {
                    floatManager?.onBubbleReplyDone()
                    if (::preferences.isInitialized) {
                        preferences.addBond(2)
                        preferences.incrementChatCount()
                        PetLogger.d(TAG, "Chat done: bond=${preferences.getBondLevel()}, chats=${preferences.getTotalChatCount()}")
                    }
                }
            }
        }
    }

    // ── 情绪等级映射 ─────────────────────────────────────────────────

    private var lastEmotionLevel: EmotionLevel = EmotionLevel.NEUTRAL
    private enum class EmotionLevel { NEGATIVE, NEUTRAL, POSITIVE }

    private fun emotionToLevel(emotion: Int): EmotionLevel = when {
        emotion <= 3 -> EmotionLevel.NEGATIVE
        emotion >= 7 -> EmotionLevel.POSITIVE
        else         -> EmotionLevel.NEUTRAL
    }

    private fun findExpressionForLevel(level: EmotionLevel, expressions: List<String>): String? {
        val keywords = when (level) {
            EmotionLevel.POSITIVE -> listOf("happy", "smile", "joy", "excited", "开心", "高兴", "快乐", "喜", "笑")
            EmotionLevel.NEGATIVE -> listOf("sad", "angry", "cry", "fear", "pain", "hurt", "难过", "伤心", "哭", "生气", "愤怒")
            EmotionLevel.NEUTRAL  -> return null
        }
        val names = expressions.map { it.removeSuffix(".exp3.json").lowercase() }
        keywords.forEach { kw -> names.indexOfFirst { it.contains(kw) }.takeIf { it >= 0 }?.let { return expressions[it] } }
        return null
    }

    private fun findMotionForLevel(level: EmotionLevel, motions: List<String>): String? {
        val keywords = when (level) {
            EmotionLevel.POSITIVE -> listOf("happy", "excited", "joy", "dance", "wave", "cheer", "开心", "高兴", "欢快", "跳舞")
            EmotionLevel.NEGATIVE -> listOf("sad", "cry", "angry", "depressed", "hurt", "难过", "伤心", "哭", "生气")
            EmotionLevel.NEUTRAL  -> listOf("idle", "normal", "relax", "calm", "breath", "待机", "放松", "呼吸")
        }
        val names = motions.map { it.removeSuffix(".motion3.json").lowercase() }
        keywords.forEach { kw -> names.indexOfFirst { it.contains(kw) }.takeIf { it >= 0 }?.let { return motions[it] } }
        if (level == EmotionLevel.NEUTRAL && motions.isNotEmpty()) return motions[0]
        return null
    }

    private fun applyEmotionExpression(emotion: Int) {
        val newLevel = emotionToLevel(emotion)
        if (newLevel == lastEmotionLevel) return
        lastEmotionLevel = newLevel
        val fm = floatManager ?: return
        val modelInfo = ModelManager.findModel(context, ModelManager.getActiveModelId(context)) ?: return
        if (modelInfo.expressions.isNotEmpty()) {
            val exp = findExpressionForLevel(newLevel, modelInfo.expressions)
            if (exp != null) fm.playExpression(exp)
            else if (newLevel != EmotionLevel.NEUTRAL) fm.playExpression("")
            PetLogger.d(TAG, "emotion=$emotion($newLevel) exp=${exp ?: "none"}")
        }
        if (modelInfo.motions.isNotEmpty()) {
            val mot = findMotionForLevel(newLevel, modelInfo.motions)
            if (mot != null) fm.playMotionFile(mot)
            PetLogger.d(TAG, "emotion=$emotion($newLevel) motion=${mot ?: "none"}")
        }
    }

    // ── 生命周期 ──────────────────────────────────────────────────────

    fun start() {
        if (isRunning) return
        isRunning = true
        serviceStartTimeMs = System.currentTimeMillis()
        lastUserInteractionMs = serviceStartTimeMs

        preferences = PetPreferences(context)
        rlBehaviorManager = RLBehaviorManager(context, scope)
        behaviorStateMachine = PetBehaviorStateMachine(rlBehaviorManager)
        preferences.saveServiceStartTime(serviceStartTimeMs)

        scope.launch(Dispatchers.IO) {
            val savedEmotion = preferences.getPetEmotion()
            rlBehaviorManager.updateEmotion(savedEmotion)
            PetLogger.d(TAG, "Restored emotion=$savedEmotion bond=${preferences.getBondLevel()} onlineMin=${preferences.getTotalOnlineMinutes()}")
        }

        textSentimentAnalyzer   = TextSentimentAnalyzer(context)
        behaviorEmotionAnalyzer = BehaviorEmotionAnalyzer(context)
        behaviorPredictor       = BehaviorPredictor(context)

        createCareNotifyChannel()

        scope.launch { periodicUpdate() }
        scope.launch { foregroundAppLoop() }
        scope.launch { bondDecayLoop() }
        scope.launch { careNotifyLoop() }

        val filter = IntentFilter().apply {
            addAction(ChatManager.ACTION_BUBBLE_TOKEN)
            addAction(ChatManager.ACTION_BUBBLE_DONE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(bubbleReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(bubbleReceiver, filter)
        }
        PetLogger.d(TAG, "Coordinator started")
    }

    fun stop() {
        isRunning = false
        cameraGestureManager?.stop()
        cameraGestureManager = null
        try { context.unregisterReceiver(bubbleReceiver) } catch (_: Exception) {}
        floatManager?.hideBubble()
        if (::rlBehaviorManager.isInitialized && ::preferences.isInitialized) {
            preferences.savePetEmotion(rlBehaviorManager.getCurrentEmotion())
            val onlineMin = (System.currentTimeMillis() - serviceStartTimeMs) / 60_000L
            if (onlineMin > 0) preferences.addOnlineMinutes(onlineMin)
            PetLogger.d(TAG, "stop: emotion=${rlBehaviorManager.getCurrentEmotion()} +${onlineMin}min total=${preferences.getTotalOnlineMinutes()}min bond=${preferences.getBondLevel()}")
        }
        PetLogger.d(TAG, "Coordinator stopped")
    }

    fun applyEmotionFromChat(emotionScore: Int) {
        lastEmotionLevel = EmotionLevel.NEUTRAL
        applyEmotionExpression(emotionScore)
        if (::rlBehaviorManager.isInitialized) rlBehaviorManager.updateEmotion(emotionScore)
    }

    fun handleUserInteraction(interaction: UserInteractionEvent) {
        if (!isRunning) return
        lastUserInteractionMs = System.currentTimeMillis()
        if (::preferences.isInitialized) {
            preferences.addBond(1)
            PetLogger.d(TAG, "Interaction bond=${preferences.getBondLevel()}")
        }
        scope.launch {
            val newState = behaviorStateMachine.handleInteraction(interaction)
            PetLogger.d(TAG, "Interaction: ${interaction.type} -> $newState")
        }
    }

    // ── 协程循环 ──────────────────────────────────────────────────────

    private suspend fun periodicUpdate() {
        while (isRunning) {
            try {
                val emotion = behaviorEmotionAnalyzer.analyzeBehaviorEmotion()
                PetLogger.d(TAG, "Emotion=$emotion")
                rlBehaviorManager.updateEmotion(emotion)
                applyEmotionExpression(emotion)

                val newState = behaviorStateMachine.updatePeriodic()
                PetLogger.d(TAG, "Periodic state=$newState")

                val preds = behaviorPredictor.predictNextHour()
                if (preds.isNotEmpty()) {
                    preds.forEachIndexed { i, p ->
                        val name   = behaviorEmotionAnalyzer.getAppName(p.packageName)
                        val minLater = (p.predictedTime - System.currentTimeMillis()) / 60_000L
                        val pct    = (p.confidence * 100).toInt()
                        PetLogger.d(TAG, "预测[${i+1}] ~${minLater}min后「$name」置信${pct}%")
                    }
                } else {
                    PetLogger.d(TAG, "行为预测：暂无（历史不足）")
                }
                delay(30_000L)
            } catch (e: Exception) {
                PetLogger.e(TAG, "periodicUpdate error", e)
                delay(10_000L)
            }
        }
    }

    private suspend fun foregroundAppLoop() {
        while (isRunning) {
            try {
                val pkg  = behaviorEmotionAnalyzer.getCurrentForegroundApp()
                val name = pkg?.let { behaviorEmotionAnalyzer.getAppName(it) } ?: "null"
                PetLogger.d(TAG, "Foreground: $pkg ($name)")
            } catch (e: Exception) {
                PetLogger.e(TAG, "foregroundAppLoop error", e)
            }
            delay(5_000L)
        }
    }

    /**
     * 亲密度衰减循环：每小时检查一次，超过 8 小时无交互则 -1
     */
    private suspend fun bondDecayLoop() {
        while (isRunning) {
            delay(60 * 60 * 1000L)
            if (!::preferences.isInitialized) continue
            val idleMs = System.currentTimeMillis() - lastUserInteractionMs
            if (idleMs > BOND_DECAY_IDLE_MS) {
                preferences.addBond(-1)
                PetLogger.d(TAG, "Bond decay: bond=${preferences.getBondLevel()} idleMs=$idleMs")
            }
        }
    }

    /**
     * 关心通知循环：每 30 分钟检查一次
     * 若超过 2 小时未交互且距上次通知超过 3 小时则发送
     */
    private suspend fun careNotifyLoop() {
        while (isRunning) {
            delay(30 * 60 * 1000L)
            if (!::preferences.isInitialized) continue
            val now       = System.currentTimeMillis()
            val idleMs    = now - lastUserInteractionMs
            val lastNotify = preferences.getLastCareNotifyTime()
            if (idleMs >= CARE_IDLE_THRESHOLD_MS && (now - lastNotify) >= CARE_NOTIFY_COOLDOWN_MS) {
                sendCareNotification()
                preferences.saveLastCareNotifyTime(now)
            }
        }
    }

    // ── 关心通知 ──────────────────────────────────────────────────────

    private fun createCareNotifyChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CARE_NOTIFY_CHANNEL,
                "宠物关心提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "桌宠定期发送关心消息"
                enableVibration(true)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun sendCareNotification() {
        try {
            val msg = careMessages.random()
            val launchIntent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?: Intent().apply { setClassName(context.packageName, "com.example.pet.MainActivity") }
            val pi = PendingIntent.getActivity(
                context, CARE_NOTIFY_ID, launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = NotificationCompat.Builder(context, CARE_NOTIFY_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("你的桌宠在想你~")
                .setContentText(msg)
                .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(CARE_NOTIFY_ID, notification)
            PetLogger.d(TAG, "Care notification sent: $msg")
        } catch (e: Exception) {
            PetLogger.e(TAG, "sendCareNotification failed", e)
        }
    }

    // ── 手势识别 ──────────────────────────────────────────────────────

    /**
     * 启动摄像头手势识别
     * @param lifecycleOwner 传入宿主 Service 对应的 LifecycleOwner
     */
    fun startGestureRecognition(lifecycleOwner: LifecycleOwner) {
        if (cameraGestureManager != null) return
        cameraGestureManager = CameraGestureManager(context, scope).also { mgr ->
            mgr.onGestureDetected = { gesture ->
                onGestureReceived(gesture)
            }
            mgr.start(lifecycleOwner)
        }
        PetLogger.d(TAG, "Gesture recognition started")
    }

    /** 停止手势识别并释放摄像头 */
    fun stopGestureRecognition() {
        cameraGestureManager?.stop()
        cameraGestureManager = null
        PetLogger.d(TAG, "Gesture recognition stopped")
    }

    /** 是否正在进行手势识别 */
    fun isGestureRecognitionRunning(): Boolean =
        cameraGestureManager?.isRunning() == true

    /**
     * 将手势映射为宠物交互事件
     * THUMBS_UP / PEACE  → CLICK（正向互动，亲密度 +1）
     * WAVE / FIST        → DOUBLE_CLICK（触发特殊动作）
     * POINT              → LONG_PRESS（唤起聊天）
     */
    private fun onGestureReceived(gesture: Gesture) {
        if (!isRunning) return
        PetLogger.d(TAG, "Gesture received: $gesture")

        // POINT 手势直接打开聊天（与长按宠物效果一致）
        if (gesture == Gesture.POINT) {
            lastUserInteractionMs = System.currentTimeMillis()
            if (::preferences.isInitialized) preferences.addBond(1)
            // 先打开聊天，300ms 后再更新情绪避免同帧卡顿
            onOpenChat?.invoke()
            scope.launch {
                kotlinx.coroutines.delay(300L)
                applyEmotionFromChat(5)
            }
            PetLogger.d(TAG, "Gesture POINT: opening chat via onOpenChat callback")
            return
        }

        // FIST 手势打开抖音
        if (gesture == Gesture.FIST) {
            lastUserInteractionMs = System.currentTimeMillis()
            if (::preferences.isInitialized) preferences.addBond(1)
            applyEmotionFromChat(6)
            launchDouyinOrToast()
            PetLogger.d(TAG, "Gesture FIST: launching Douyin")
            return
        }

        // THUMBS_UP 手势打开微信
        if (gesture == Gesture.THUMBS_UP) {
            lastUserInteractionMs = System.currentTimeMillis()
            if (::preferences.isInitialized) preferences.addBond(1)
            applyEmotionFromChat(8)
            launchWechatOrToast()
            PetLogger.d(TAG, "Gesture THUMBS_UP: launching WeChat")
            return
        }

        // PEACE 手势返回桌面
        if (gesture == Gesture.PEACE) {
            lastUserInteractionMs = System.currentTimeMillis()
            if (::preferences.isInitialized) preferences.addBond(1)
            applyEmotionFromChat(7)
            goHome()
            PetLogger.d(TAG, "Gesture PEACE: go home")
            return
        }

        // WAVE 手势在当前前台 App 执行下滑
        if (gesture == Gesture.WAVE) {
            lastUserInteractionMs = System.currentTimeMillis()
            if (::preferences.isInitialized) preferences.addBond(1)
            applyEmotionFromChat(6)
            performSwipeDown()
            PetLogger.d(TAG, "Gesture WAVE: swipe down")
            return
        }

        // 其他手势无操作
        return
    }

    /**
     * 通过 AccessibilityService 在当前前台 App 执行下滑手势
     * 若无障碍服务未开启则弹 Toast 提示
     */
    private fun performSwipeDown() {
        if (onSwipeDown != null) {
            onSwipeDown?.invoke()
            PetLogger.d(TAG, "performSwipeDown: dispatched via callback")
        } else {
            scope.launch(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    context,
                    "请先在「设置 → 无障碍」中开启 Pet Desktop 手势控制服务",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            PetLogger.w(TAG, "onSwipeDown callback not set, AccessibilityService may not be running")
        }
    }

    /**
     * 返回桌面（发送 HOME Intent）
     */
    private fun goHome() {
        try {
            val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_HOME)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(homeIntent)
            PetLogger.d(TAG, "Go home successfully")
        } catch (e: Exception) {
            PetLogger.e(TAG, "goHome failed", e)
        }
    }

    /**
     * 打开微信，若未安装则弹 Toast 提示
     * 微信包名：com.tencent.mm
     */
    private fun launchWechatOrToast() {
        val wechatPackage = "com.tencent.mm"
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(wechatPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                PetLogger.d(TAG, "WeChat launched successfully")
            } else {
                scope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "未检测到微信，请先安装微信~",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                PetLogger.d(TAG, "WeChat not installed")
            }
        } catch (e: Exception) {
            PetLogger.e(TAG, "launchWechatOrToast failed", e)
        }
    }

    // ── 应用启动工具 ──────────────────────────────────────────────────

    /**
     * 打开抖音，若未安装则弹 Toast 提示
     * 抖音包名：com.ss.android.ugc.aweme
     */
    private fun launchDouyinOrToast() {
        val douyinPackage = "com.ss.android.ugc.aweme"
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(douyinPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                PetLogger.d(TAG, "Douyin launched successfully")
            } else {
                // 抖音未安装，在主线程弹 Toast
                scope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "未检测到抖音，请先安装抖音~",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                PetLogger.d(TAG, "Douyin not installed")
            }
        } catch (e: Exception) {
            PetLogger.e(TAG, "launchDouyinOrToast failed", e)
        }
    }
}

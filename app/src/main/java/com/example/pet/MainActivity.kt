package com.example.pet

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.pet.core.common.logger.PetLogger
import com.pet.core.data.preferences.PetPreferences
import com.pet.core.domain.usecase.CheckPermissionsUseCase
import com.pet.pet.service.PetForegroundService

class MainActivity : AppCompatActivity() {

    private val checkPermissionsUseCase by lazy { CheckPermissionsUseCase(this) }
    private val petPreferences by lazy { PetPreferences(this) }

    private lateinit var tvPermission: TextView
    private lateinit var tvService: TextView
    private lateinit var tvAlgo: TextView
    private lateinit var tvEmotionEmoji: TextView
    private lateinit var tvEmotionValue: TextView
    private lateinit var tvBondEmoji: TextView
    private lateinit var tvBondValue: TextView
    private lateinit var tvOnlineTime: TextView
    private lateinit var tvChatCount: TextView
    private lateinit var progressBond: ProgressBar
    private lateinit var btnRefresh: MaterialButton
    private lateinit var btnStart: MaterialButton
    private lateinit var btnStop: MaterialButton
    private lateinit var btnPreviewMotion: MaterialButton
    private lateinit var btnSwitchModel: MaterialButton
    private lateinit var switchGesture: SwitchMaterial
    private lateinit var tvGestureStatus: TextView

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            PetLogger.d("MainActivity", "Camera permission granted")
            enableGestureRecognition()
        } else {
            PetLogger.w("MainActivity", "Camera permission denied")
            switchGesture.isChecked = false
            showPermissionDeniedDialog("摄像头权限")
        }
    }

    private val requestOverlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> refreshStatus() }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            PetLogger.d("MainActivity", "Notification permission granted")
            refreshStatus()
        } else {
            PetLogger.w("MainActivity", "Notification permission denied")
            showPermissionDeniedDialog("通知权限")
            refreshStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initViews()
        bindActions()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        // 每次回到 MainActivity 刷新宠物状态（含无障碍状态）
        refreshPetStats()
        // 用户可能从无障碍设置页返回，刷新手势状态文字
        val gestureEnabled = petPreferences.isGestureRecognitionEnabled()
        updateGestureStatusText(gestureEnabled)
    }

    private fun initViews() {
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = getString(R.string.ui_title)
        }
        tvPermission    = findViewById(R.id.tvPermission)
        tvService       = findViewById(R.id.tvService)
        tvAlgo          = findViewById(R.id.tvAlgo)
        tvEmotionEmoji  = findViewById(R.id.tvEmotionEmoji)
        tvEmotionValue  = findViewById(R.id.tvEmotionValue)
        tvBondEmoji     = findViewById(R.id.tvBondEmoji)
        tvBondValue     = findViewById(R.id.tvBondValue)
        tvOnlineTime    = findViewById(R.id.tvOnlineTime)
        tvChatCount     = findViewById(R.id.tvChatCount)
        progressBond    = findViewById(R.id.progressBond)
        btnRefresh      = findViewById(R.id.btnRefresh)
        btnStart        = findViewById(R.id.btnStart)
        btnStop         = findViewById(R.id.btnStop)
        btnPreviewMotion = findViewById(R.id.btnPreviewMotion)
        btnSwitchModel  = findViewById(R.id.btnSwitchModel)
        switchGesture   = findViewById(R.id.switchGesture)
        tvGestureStatus = findViewById(R.id.tvGestureStatus)
    }

    private fun bindActions() {
        btnRefresh.setOnClickListener { refreshStatus(); refreshPetStats() }
        btnStart.setOnClickListener { checkAndRequestPermissionsThenStartService() }
        btnStop.setOnClickListener { stopPetService() }
        btnPreviewMotion.setOnClickListener {
            if (com.pet.pet.service.manager.PetServiceManager.isServiceRunning(this)) {
                AlertDialog.Builder(this)
                    .setTitle("无法打开预览")
                    .setMessage("桌宠服务正在运行中，请先停止服务再打开模型预览页面。")
                    .setPositiveButton("去停止服务") { _, _ -> stopPetService() }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                startActivity(Intent(this, MotionPreviewActivity::class.java))
            }
        }
        btnSwitchModel.setOnClickListener {
            startActivity(Intent(this, ModelSwitchActivity::class.java))
        }

        // 恢复手势开关的持久化状态
        val gestureEnabled = petPreferences.isGestureRecognitionEnabled()
        switchGesture.isChecked = gestureEnabled
        updateGestureStatusText(gestureEnabled)

        switchGesture.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!com.pet.pet.service.manager.PetServiceManager.isServiceRunning(this)) {
                    AlertDialog.Builder(this)
                        .setTitle("服务未运行")
                        .setMessage("请先启动桌宠服务，再开启手势识别。")
                        .setPositiveButton("去启动") { _, _ ->
                            switchGesture.isChecked = false
                            checkAndRequestPermissionsThenStartService()
                        }
                        .setNegativeButton("取消") { _, _ ->
                            switchGesture.isChecked = false
                        }
                        .show()
                    return@setOnCheckedChangeListener
                }
                // 检查摄像头权限
                if (checkSelfPermission(Manifest.permission.CAMERA) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                } else {
                    enableGestureRecognition()
                }
            } else {
                disableGestureRecognition()
            }
        }
    }

    private fun enableGestureRecognition() {
        // 检查无障碍服务是否已开启（用于上滑手势）
        if (!isAccessibilityServiceEnabled()) {
            showAccessibilityGuideDialog()
        }
        PetForegroundService.cmdStartGesture(this)
        petPreferences.setGestureRecognitionEnabled(true)
        updateGestureStatusText(true)
        PetLogger.d("MainActivity", "Gesture recognition enabled")
    }

    private fun disableGestureRecognition() {
        PetForegroundService.cmdStopGesture(this)
        petPreferences.setGestureRecognitionEnabled(false)
        updateGestureStatusText(false)
        PetLogger.d("MainActivity", "Gesture recognition disabled")
    }

    private fun updateGestureStatusText(enabled: Boolean) {
        val accessibilityOk = isAccessibilityServiceEnabled()
        tvGestureStatus.text = when {
            !enabled          -> "状态：未开启"
            !accessibilityOk  -> "状态：✓ 运行中（⚠️ 无障碍未开启，上滑功能不可用）"
            else              -> "状态：✓ 手势识别运行中（无障碍 ✓）"
        }
        tvGestureStatus.setTextColor(
            when {
                !enabled         -> android.graphics.Color.parseColor("#88AAAACC")
                !accessibilityOk -> android.graphics.Color.parseColor("#FFB74D")
                else             -> android.graphics.Color.parseColor("#BBA0FF")
            }
        )
    }

    /**
     * 检测 PetAccessibilityService 是否已在系统无障碍设置中开启
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = ComponentName(this, PetAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(":")
            .any { it.equals(expectedComponent.flattenToString(), ignoreCase = true) }
    }

    /**
     * 弹出引导对话框，一键跳转无障碍设置页面
     */
    private fun showAccessibilityGuideDialog() {
        AlertDialog.Builder(this)
            .setTitle("开启无障碍服务")
            .setMessage("挥手上滑功能需要「Pet Desktop 手势控制」无障碍服务支持。\n\n请在下一页面找到「Pet Desktop 手势控制」并开启，之后返回即可使用上滑功能。")
            .setPositiveButton("去开启") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("暂不开启", null)
            .show()
    }

    /**
     * 刷新宠物状态面板（情绪、亲密度、在线时长、聊天次数）
     */
    private fun refreshPetStats() {
        val emotion   = petPreferences.getPetEmotion()
        val bond      = petPreferences.getBondLevel()
        val onlineMin = petPreferences.getTotalOnlineMinutes()
        val chatCount = petPreferences.getTotalChatCount()

        // 情绪 emoji 映射
        val (emotionEmoji, emotionLabel) = when {
            emotion >= 8 -> "😄" to "开心 ($emotion/10)"
            emotion >= 6 -> "😊" to "愉快 ($emotion/10)"
            emotion >= 4 -> "😐" to "平静 ($emotion/10)"
            emotion >= 2 -> "😔" to "低落 ($emotion/10)"
            else         -> "😢" to "难过 ($emotion/10)"
        }
        tvEmotionEmoji.text = emotionEmoji
        tvEmotionValue.text = "情绪: $emotionLabel"

        // 亲密度 emoji 映射
        val (bondEmoji, bondLabel) = when {
            bond >= 80 -> "❤️" to "挚爱 ($bond)"
            bond >= 60 -> "🧡" to "亲密 ($bond)"
            bond >= 40 -> "💛" to "喜欢 ($bond)"
            bond >= 20 -> "💙" to "熟悉 ($bond)"
            else       -> "🤍" to "陌生 ($bond)"
        }
        tvBondEmoji.text = bondEmoji
        tvBondValue.text = "亲密: $bondLabel"
        progressBond.progress = bond

        // 在线时长格式化
        val hours = onlineMin / 60
        val mins  = onlineMin % 60
        tvOnlineTime.text = if (hours > 0) "在线: ${hours}h${mins}m" else "在线: ${mins}min"

        // 聊天次数
        tvChatCount.text = "聊天: ${chatCount}次"

        // 算法状态更新
        tvAlgo.text = "Q-Learning ✓ | 情绪分析 ✓ | 行为预测 ✓ | 情绪=$emotion 亲密=$bond"
    }

    private fun refreshStatus() {
        val overlayGranted = when (val r = checkPermissionsUseCase.checkOverlayPermission()) {
            is com.pet.core.common.result.Result.Success -> r.data
            else -> false
        }
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
        val usageStatsGranted = hasUsageStatsPermission()
        val serviceRunning = com.pet.pet.service.manager.PetServiceManager.isServiceRunning(this)

        tvPermission.text = buildString {
            append(getString(
                R.string.ui_permission_state_fmt,
                if (overlayGranted) getString(R.string.ui_permission_granted) else getString(R.string.ui_permission_denied),
                if (notificationGranted) getString(R.string.ui_permission_granted) else getString(R.string.ui_permission_denied)
            ))
            append("\n使用情况访问：")
            append(if (usageStatsGranted) "✓ 已授权" else "✗ 未授权（情绪分析降级）")
        }

        tvService.text = if (serviceRunning) "服务状态：● 运行中" else "服务状态：○ 未运行"

        refreshPetStats()
    }

    private fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) { false }
    }

    private fun requestUsageStatsPermission() {
        AlertDialog.Builder(this)
            .setTitle("需要使用情况访问权限")
            .setMessage("情绪分析功能需要读取应用使用情况，请在下一页面找到 \"Pet Desktop\" 并开启。")
            .setPositiveButton("去开启") { _, _ ->
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setNegativeButton("跳过") { _, _ -> startPetService() }
            .show()
    }

    private fun checkAndRequestPermissionsThenStartService() {
        when (val r = checkPermissionsUseCase.checkOverlayPermission()) {
            is com.pet.core.common.result.Result.Success -> {
                if (!r.data) { requestOverlayPermission(); return }
            }
            is com.pet.core.common.result.Result.Error ->
                PetLogger.e("MainActivity", "overlay check failed", r.exception)
            else -> {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        if (!hasUsageStatsPermission()) { requestUsageStatsPermission(); return }
        startPetService()
    }

    private fun requestOverlayPermission() {
        requestOverlayPermissionLauncher.launch(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
        )
    }

    private fun startPetService() {
        PetLogger.d("MainActivity", "Starting pet service...")
        try {
            com.pet.pet.service.manager.PetServiceManager.startService(this)
            tvService.text = getString(R.string.ui_service_start_requested)
        } catch (e: Exception) {
            PetLogger.e("MainActivity", "Failed to start pet service", e)
            tvService.text = getString(R.string.ui_service_start_failed_fmt, e.message ?: "unknown")
        }
    }

    private fun stopPetService() {
        PetLogger.d("MainActivity", "Stopping pet service...")
        try {
            com.pet.pet.service.manager.PetServiceManager.stopService(this)
            tvService.text = getString(R.string.ui_service_stop_requested)
        } catch (e: Exception) {
            PetLogger.e("MainActivity", "Failed to stop pet service", e)
            tvService.text = getString(R.string.ui_service_stop_failed_fmt, e.message ?: "unknown")
        }
    }

    private fun showPermissionDeniedDialog(permissionName: String) {
        AlertDialog.Builder(this)
            .setTitle("权限被拒绝")
            .setMessage("$permissionName 被拒绝，部分功能无法使用。\n\n请在设置中手动授予权限。")
            .setPositiveButton("去设置") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
            .setNegativeButton("取消", null)
            .show()
    }
}

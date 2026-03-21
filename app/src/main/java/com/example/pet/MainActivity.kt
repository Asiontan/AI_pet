package com.example.pet

import android.Manifest
import android.app.AppOpsManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.pet.core.common.logger.PetLogger
import com.pet.core.domain.usecase.CheckPermissionsUseCase

class MainActivity : AppCompatActivity() {

    private val checkPermissionsUseCase by lazy { CheckPermissionsUseCase(this) }

    private lateinit var tvPermission: android.widget.TextView
    private lateinit var tvService: android.widget.TextView
    private lateinit var tvAlgo: android.widget.TextView
    private lateinit var btnRefresh: MaterialButton
    private lateinit var btnStart: MaterialButton
    private lateinit var btnStop: MaterialButton
    private lateinit var btnPreviewMotion: MaterialButton
    private lateinit var btnSwitchModel: MaterialButton

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

    private fun initViews() {
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = getString(R.string.ui_title)
        }
        tvPermission = findViewById(R.id.tvPermission)
        tvService = findViewById(R.id.tvService)
        tvAlgo = findViewById(R.id.tvAlgo)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnPreviewMotion = findViewById(R.id.btnPreviewMotion)
        btnSwitchModel = findViewById(R.id.btnSwitchModel)
    }

    private fun bindActions() {
        btnRefresh.setOnClickListener { refreshStatus() }
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
                startActivity(android.content.Intent(this, MotionPreviewActivity::class.java))
            }
        }
        btnSwitchModel.setOnClickListener {
            startActivity(android.content.Intent(this, ModelSwitchActivity::class.java))
        }
    }

    private fun refreshStatus() {
        val overlayGranted = when (val overlayResult = checkPermissionsUseCase.checkOverlayPermission()) {
            is com.pet.core.common.result.Result.Success -> overlayResult.data
            else -> false
        }
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val usageStatsGranted = hasUsageStatsPermission()

        tvPermission.text = getString(
            R.string.ui_permission_state_fmt,
            if (overlayGranted) getString(R.string.ui_permission_granted) else getString(R.string.ui_permission_denied),
            if (notificationGranted) getString(R.string.ui_permission_granted) else getString(R.string.ui_permission_denied)
        ) + "\n使用情况访问权限：" +
            if (usageStatsGranted) getString(R.string.ui_permission_granted)
            else "${getString(R.string.ui_permission_denied)}（情绪分析不可用）"

        tvService.text = getString(R.string.ui_service_hint)
        tvAlgo.text = getString(R.string.ui_algo_placeholder)
    }

    /**
     * 检查 PACKAGE_USAGE_STATS 是否已被用户授权
     * （即使 Manifest 中已声明，也需要用户在「设置→有权查看使用情况的应用」中手动开启）
     */
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
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 弹窗引导用户前往系统设置开启使用情况访问权限
     */
    private fun requestUsageStatsPermission() {
        AlertDialog.Builder(this)
            .setTitle("需要使用情况访问权限")
            .setMessage("情绪分析功能需要读取应用使用情况，请在下一页面中找到 \"Pet Desktop\" 并开启权限。")
            .setPositiveButton("去开启") { _, _ ->
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setNegativeButton("跳过") { _, _ ->
                // 跳过不影响服务启动，情绪分析将返回默认中性值 5
                startPetService()
            }
            .show()
    }

    private fun checkAndRequestPermissionsThenStartService() {
        // 1. 检查悬浮窗权限
        when (val overlayResult = checkPermissionsUseCase.checkOverlayPermission()) {
            is com.pet.core.common.result.Result.Success -> {
                if (!overlayResult.data) {
                    requestOverlayPermission()
                    return
                }
            }
            is com.pet.core.common.result.Result.Error -> {
                PetLogger.e("MainActivity", "Failed to check overlay permission", overlayResult.exception)
            }
            else -> {}
        }

        // 2. 检查通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        // 3. 检查使用情况访问权限（非强制，跳过也可启动，情绪分析降级为中性值）
        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission()
            return
        }

        // 所有权限就绪，启动服务
        startPetService()
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        requestOverlayPermissionLauncher.launch(intent)
    }

    private fun startPetService() {
        PetLogger.d("MainActivity", "Starting pet service...")
        try {
            com.pet.pet.service.manager.PetServiceManager.startService(this)
            PetLogger.d("MainActivity", "Pet service started successfully")
            tvService.text = getString(R.string.ui_service_start_requested)
        } catch (e: Exception) {
            PetLogger.e("MainActivity", "Failed to start pet service", e)
            showServiceStartingMessage("启动失败：${e.message}")
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

    private fun showServiceStartingMessage(message: String = "服务已启动") {
        AlertDialog.Builder(this)
            .setTitle("Pet Desktop")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    private fun showPermissionDeniedDialog(permissionName: String) {
        AlertDialog.Builder(this)
            .setTitle("权限被拒绝")
            .setMessage("$permissionName 被拒绝，部分功能可能无法正常使用。\n\n请在设置中手动授予权限。")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }
}

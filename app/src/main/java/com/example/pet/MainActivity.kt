package com.example.pet

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
    
    private val requestOverlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        refreshStatus()
    }
    
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
    }

    private fun bindActions() {
        btnRefresh.setOnClickListener { refreshStatus() }
        btnStart.setOnClickListener { checkAndRequestPermissionsThenStartService() }
        btnStop.setOnClickListener { stopPetService() }
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

        tvPermission.text = getString(
            R.string.ui_permission_state_fmt,
            if (overlayGranted) getString(R.string.ui_permission_granted) else getString(R.string.ui_permission_denied),
            if (notificationGranted) getString(R.string.ui_permission_granted) else getString(R.string.ui_permission_denied)
        )

        // Android限制下无法可靠读取“服务是否正在运行”，这里展示“控制台视角”的状态即可
        tvService.text = getString(R.string.ui_service_hint)
        tvAlgo.text = getString(R.string.ui_algo_placeholder)
    }
    
    private fun checkAndRequestPermissionsThenStartService() {
        // 检查悬浮窗权限
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
        
        // 检查通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != 
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        
        // 所有权限都已授予，启动服务
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
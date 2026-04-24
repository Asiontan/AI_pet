package com.example.pet

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.pet.core.common.logger.PetLogger
import com.pet.core.common.result.Result
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LoginActivity"
    }

    private val app by lazy { PetApplication.instance }

    private lateinit var cardLoginForm: MaterialCardView
    private lateinit var cardLoggedIn: MaterialCardView
    private lateinit var tabMode: TabLayout
    private lateinit var etUsername: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnSubmit: MaterialButton
    private lateinit var tvMessage: TextView
    private lateinit var progressLoading: ProgressBar

    private lateinit var tvLoggedUsername: TextView
    private lateinit var tvSyncStatus: TextView
    private lateinit var btnSyncUpload: MaterialButton
    private lateinit var btnSyncDownload: MaterialButton
    private lateinit var btnLogout: MaterialButton

    private var isRegisterMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.loginRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initViews()
        bindActions()
        refreshUI()
    }

    private fun initViews() {
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        cardLoginForm   = findViewById(R.id.cardLoginForm)
        cardLoggedIn    = findViewById(R.id.cardLoggedIn)
        tabMode         = findViewById(R.id.tabMode)
        etUsername       = findViewById(R.id.etUsername)
        etPassword       = findViewById(R.id.etPassword)
        btnSubmit       = findViewById(R.id.btnSubmit)
        tvMessage       = findViewById(R.id.tvMessage)
        progressLoading = findViewById(R.id.progressLoading)

        tvLoggedUsername = findViewById(R.id.tvLoggedUsername)
        tvSyncStatus    = findViewById(R.id.tvSyncStatus)
        btnSyncUpload   = findViewById(R.id.btnSyncUpload)
        btnSyncDownload = findViewById(R.id.btnSyncDownload)
        btnLogout       = findViewById(R.id.btnLogout)
    }

    private fun bindActions() {
        tabMode.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                isRegisterMode = tab.position == 1
                btnSubmit.text = if (isRegisterMode) "注册" else "登录"
                hideMessage()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        btnSubmit.setOnClickListener { submit() }
        btnSyncUpload.setOnClickListener { syncUpload() }
        btnSyncDownload.setOnClickListener { syncDownload() }
        btnLogout.setOnClickListener { confirmLogout() }
    }

    private fun refreshUI() {
        if (app.bmobRepository.isLoggedIn()) {
            cardLoginForm.visibility = View.GONE
            cardLoggedIn.visibility = View.VISIBLE
            tvLoggedUsername.text = app.petPreferences.getBmobUsername()
            tvSyncStatus.text = "已连接云端"
            tvSyncStatus.setTextColor(0xFF88CC88.toInt())
        } else {
            cardLoginForm.visibility = View.VISIBLE
            cardLoggedIn.visibility = View.GONE
        }
    }

    private fun submit() {
        val username = etUsername.text?.toString()?.trim() ?: ""
        val password = etPassword.text?.toString()?.trim() ?: ""

        if (username.length < 3) {
            showMessage("用户名至少 3 个字符", isError = true); return
        }
        if (password.length < 6) {
            showMessage("密码至少 6 个字符", isError = true); return
        }

        setLoading(true)
        hideMessage()

        lifecycleScope.launch {
            val result = if (isRegisterMode) {
                app.bmobRepository.register(username, password)
            } else {
                app.bmobRepository.login(username, password)
            }

            setLoading(false)

            when (result) {
                is Result.Success -> {
                    val actionText = if (isRegisterMode) "注册" else "登录"
                    showMessage("${actionText}成功！", isError = false)
                    PetLogger.i(TAG, "$actionText 成功: ${result.data.username}")
                    refreshUI()
                }
                is Result.Error -> {
                    showMessage(result.exception.message ?: "操作失败", isError = true)
                }
                is Result.Loading -> {}
            }
        }
    }

    private fun syncUpload() {
        btnSyncUpload.isEnabled = false
        tvSyncStatus.text = "正在上传..."
        tvSyncStatus.setTextColor(0xFFBBA0FF.toInt())

        lifecycleScope.launch {
            when (val result = app.cloudSyncManager.syncToCloud()) {
                is Result.Success -> {
                    tvSyncStatus.text = "上传成功"
                    tvSyncStatus.setTextColor(0xFF88CC88.toInt())
                    Toast.makeText(this@LoginActivity, "数据已同步到云端", Toast.LENGTH_SHORT).show()
                }
                is Result.Error -> {
                    tvSyncStatus.text = "上传失败: ${result.exception.message}"
                    tvSyncStatus.setTextColor(0xFFFF6B6B.toInt())
                }
                is Result.Loading -> {}
            }
            btnSyncUpload.isEnabled = true
        }
    }

    private fun syncDownload() {
        AlertDialog.Builder(this)
            .setTitle("确认恢复")
            .setMessage("从云端恢复数据将覆盖本地的宠物状态（情绪、亲密度等），确定继续吗？")
            .setPositiveButton("确认恢复") { _, _ -> doSyncDownload() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doSyncDownload() {
        btnSyncDownload.isEnabled = false
        tvSyncStatus.text = "正在恢复..."
        tvSyncStatus.setTextColor(0xFFBBA0FF.toInt())

        lifecycleScope.launch {
            when (val result = app.cloudSyncManager.syncFromCloud()) {
                is Result.Success -> {
                    tvSyncStatus.text = "恢复成功"
                    tvSyncStatus.setTextColor(0xFF88CC88.toInt())
                    Toast.makeText(this@LoginActivity, "已从云端恢复数据", Toast.LENGTH_SHORT).show()
                }
                is Result.Error -> {
                    tvSyncStatus.text = "恢复失败: ${result.exception.message}"
                    tvSyncStatus.setTextColor(0xFFFF6B6B.toInt())
                }
                is Result.Loading -> {}
            }
            btnSyncDownload.isEnabled = true
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("退出登录")
            .setMessage("退出后本地数据不会被删除，但将无法同步到云端。")
            .setPositiveButton("退出") { _, _ ->
                lifecycleScope.launch {
                    app.bmobRepository.logout()
                    refreshUI()
                    Toast.makeText(this@LoginActivity, "已退出登录", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setLoading(loading: Boolean) {
        progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
        btnSubmit.isEnabled = !loading
        etUsername.isEnabled = !loading
        etPassword.isEnabled = !loading
    }

    private fun showMessage(msg: String, isError: Boolean) {
        tvMessage.visibility = View.VISIBLE
        tvMessage.text = msg
        tvMessage.setTextColor(
            if (isError) 0xFFFF6B6B.toInt() else 0xFF88CC88.toInt()
        )
    }

    private fun hideMessage() {
        tvMessage.visibility = View.GONE
    }
}

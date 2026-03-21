package com.example.pet

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.pet.core.data.model.ModelManager
import com.pet.pet.service.PetForegroundService

class ModelSwitchActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ModelAdapter
    private var activeModelId: String = ""

    private val pickZipLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        importModel(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_switch)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.modelSwitchRoot)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        activeModelId = ModelManager.getActiveModelId(this)

        recyclerView = findViewById(R.id.rvModels)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = ModelAdapter(
            models = ModelManager.getAllModels(this),
            activeModelId = activeModelId,
            onSelect = { model ->
                // 1. 持久化激活模型
                activeModelId = model.id
                ModelManager.setActiveModel(this, model.id)
                adapter.setActive(model.id)
                // 2. 仅在服务已运行时才通知悬浮窗切换，否则下次开启服务时自动加载
                if (isServiceRunning()) {
                    PetForegroundService.cmdSwitchModel(
                        this,
                        model.modelJsonPath,
                        !model.isBuiltin
                    )
                    Toast.makeText(this, "已切换到 ${model.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "已选择 ${model.name}，启动服务后生效", Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = { model ->
                AlertDialog.Builder(this)
                    .setTitle("删除模型")
                    .setMessage("确认删除 ${model.name}？")
                    .setPositiveButton("删除") { _, _ ->
                        ModelManager.deleteUserModel(this, model.id)
                        refreshList()
                        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            },
            onPlayExpression = { model, expName ->
                if (isServiceRunning()) {
                    PetForegroundService.cmdSwitchModel(this, model.modelJsonPath, !model.isBuiltin)
                    PetForegroundService.cmdPlayExpression(this, expName)
                } else {
                    Toast.makeText(this, "请先启动服务", Toast.LENGTH_SHORT).show()
                }
            },
            onPlayMotion = { model, motName ->
                if (isServiceRunning()) {
                    PetForegroundService.cmdSwitchModel(this, model.modelJsonPath, !model.isBuiltin)
                    PetForegroundService.cmdPlayMotion(this, motName)
                } else {
                    Toast.makeText(this, "请先启动服务", Toast.LENGTH_SHORT).show()
                }
            }
        )
        recyclerView.adapter = adapter

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<FloatingActionButton>(R.id.fabImport).setOnClickListener {
            pickZipLauncher.launch("application/zip")
        }
    }

    private fun importModel(uri: Uri) {
        val input = android.widget.EditText(this).apply {
            hint = "请输入模型名称"
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("导入模型")
            .setView(input)
            .setPositiveButton("导入") { _, _ ->
                val name = input.text.toString().trim().ifBlank { "model_${System.currentTimeMillis()}" }
                doImport(uri, name)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doImport(uri: Uri, name: String) {
        val loadingDialog = AlertDialog.Builder(this)
            .setMessage("导入中，请稍候...")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        Thread {
            val result = try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    ModelManager.importModelFromZip(this, stream, name)
                }
            } catch (e: Exception) { null }
            runOnUiThread {
                loadingDialog.dismiss()
                if (result != null) {
                    Toast.makeText(this, "导入成功：${result.name}", Toast.LENGTH_SHORT).show()
                    refreshList()
                } else {
                    Toast.makeText(this, "导入失败，请确认 zip 格式正确", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun refreshList() {
        adapter.updateModels(ModelManager.getAllModels(this))
    }

    /** 检查 PetForegroundService 是否正在运行 */
    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(android.app.ActivityManager::class.java)
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == com.pet.pet.service.PetForegroundService::class.java.name }
    }

    // ----------------------------------------------------------------
    // Adapter
    // ----------------------------------------------------------------

    class ModelAdapter(
        private var models: List<ModelManager.ModelInfo>,
        private var activeModelId: String,
        private val onSelect: (ModelManager.ModelInfo) -> Unit,
        private val onDelete: (ModelManager.ModelInfo) -> Unit,
        private val onPlayExpression: (ModelManager.ModelInfo, String) -> Unit,
        private val onPlayMotion: (ModelManager.ModelInfo, String) -> Unit
    ) : RecyclerView.Adapter<ModelAdapter.VH>() {

        private val expandedIds = mutableSetOf<String>()

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView            = view.findViewById(R.id.tvModelName)
            val tvBadge: TextView           = view.findViewById(R.id.tvModelBadge)
            val tvActive: TextView          = view.findViewById(R.id.tvModelActive)
            val tvExpCount: TextView        = view.findViewById(R.id.tvExpCount)
            val tvMotionCount: TextView     = view.findViewById(R.id.tvMotionCount)
            val tvExpand: TextView          = view.findViewById(R.id.tvExpand)
            val layoutExpMotion: LinearLayout = view.findViewById(R.id.layoutExpMotion)
            val tvExpHeader: TextView       = view.findViewById(R.id.tvExpHeader)
            val tvMotionHeader: TextView    = view.findViewById(R.id.tvMotionHeader)
            val chipGroupExp: ChipGroup     = view.findViewById(R.id.chipGroupExpressions)
            val chipGroupMot: ChipGroup     = view.findViewById(R.id.chipGroupMotions)
            val btnSelect: MaterialButton   = view.findViewById(R.id.btnSelectModel)
            val btnDelete: MaterialButton   = view.findViewById(R.id.btnDeleteModel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_model, parent, false)
            return VH(v)
        }

        override fun getItemCount() = models.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val model = models[position]
            val isActive = model.id == activeModelId
            val isExpanded = model.id in expandedIds
            val hasExpMot = model.expressions.isNotEmpty() || model.motions.isNotEmpty()

            holder.tvName.text = model.name
            holder.tvBadge.text = if (model.isBuiltin) "内置" else "自定义"
            holder.tvBadge.setBackgroundResource(
                if (model.isBuiltin) R.drawable.badge_builtin else R.drawable.badge_custom
            )
            holder.tvActive.visibility = if (isActive) View.VISIBLE else View.GONE
            holder.btnSelect.text = if (isActive) "使用中" else "切换"
            holder.btnSelect.isEnabled = !isActive
            holder.btnSelect.setOnClickListener { onSelect(model) }
            holder.btnDelete.visibility = if (model.isBuiltin) View.GONE else View.VISIBLE
            holder.btnDelete.setOnClickListener { onDelete(model) }

            holder.tvExpCount.text = "表情: ${model.expressions.size}"
            holder.tvMotionCount.text = "动作: ${model.motions.size}"

            if (hasExpMot) {
                holder.tvExpand.visibility = View.VISIBLE
                holder.tvExpand.text = if (isExpanded) "▲ 收起" else "▼ 预览动作"
                holder.tvExpand.setOnClickListener {
                    if (isExpanded) expandedIds.remove(model.id) else expandedIds.add(model.id)
                    notifyItemChanged(position)
                }
            } else {
                holder.tvExpand.visibility = View.GONE
            }

            holder.layoutExpMotion.visibility = if (isExpanded) View.VISIBLE else View.GONE
            if (isExpanded) {
                holder.chipGroupExp.removeAllViews()
                if (model.expressions.isNotEmpty()) {
                    holder.tvExpHeader.visibility = View.VISIBLE
                    model.expressions.forEach { expName ->
                        val chip = Chip(holder.chipGroupExp.context).apply {
                            text = expName.removeSuffix(".exp3.json")
                            isClickable = true
                            isCheckable = false
                        }
                        chip.setOnClickListener { onPlayExpression(model, expName) }
                        holder.chipGroupExp.addView(chip)
                    }
                } else {
                    holder.tvExpHeader.visibility = View.GONE
                }

                holder.chipGroupMot.removeAllViews()
                if (model.motions.isNotEmpty()) {
                    holder.tvMotionHeader.visibility = View.VISIBLE
                    model.motions.forEach { motName ->
                        val chip = Chip(holder.chipGroupMot.context).apply {
                            text = motName.removeSuffix(".motion3.json")
                            isClickable = true
                            isCheckable = false
                        }
                        chip.setOnClickListener { onPlayMotion(model, motName) }
                        holder.chipGroupMot.addView(chip)
                    }
                } else {
                    holder.tvMotionHeader.visibility = View.GONE
                }
            }
        }

        fun setActive(id: String) {
            activeModelId = id
            notifyDataSetChanged()
        }

        fun updateModels(newModels: List<ModelManager.ModelInfo>) {
            models = newModels
            notifyDataSetChanged()
        }
    }
}

package com.example.pet

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.pet.core.data.model.ModelManager
import com.pet.pet.render.view.Live2DPetView

class MotionPreviewActivity : AppCompatActivity() {

    private lateinit var live2dView: Live2DPetView
    private lateinit var tvCurrentAction: TextView
    private lateinit var tvExpHeader: TextView
    private lateinit var tvMotHeader: TextView
    private lateinit var chipGroupExp: ChipGroup
    private lateinit var chipGroupMot: ChipGroup
    private lateinit var tvNoAction: TextView
    private lateinit var spinnerModel: Spinner

    private var models: List<ModelManager.ModelInfo> = emptyList()
    private var currentModel: ModelManager.ModelInfo? = null
    private var activeExpName: String? = null  // 当前激活的表情文件名

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_motion_preview)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.motionPreviewRoot)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        live2dView     = findViewById(R.id.live2dPreviewView)
        live2dView.modelDisplaySizeDp = 200f  // 预览页放大显示
        tvCurrentAction = findViewById(R.id.tvCurrentMotion)
        tvExpHeader    = findViewById(R.id.tvExpHeader)
        tvMotHeader    = findViewById(R.id.tvMotHeader)
        chipGroupExp   = findViewById(R.id.chipGroupExp)
        chipGroupMot   = findViewById(R.id.chipGroupMot)
        tvNoAction     = findViewById(R.id.tvNoAction)
        spinnerModel   = findViewById(R.id.spinnerModel)

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }

        models = ModelManager.getAllModels(this)
        val activeId = ModelManager.getActiveModelId(this)
        val names = models.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerModel.adapter = adapter

        // 默认选中激活模型
        val activeIdx = models.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
        spinnerModel.setSelection(activeIdx)

        spinnerModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                switchToModel(models[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun switchToModel(model: ModelManager.ModelInfo) {
        currentModel = model
        // 切换 Live2D 视图模型
        val source = if (model.isBuiltin)
            Live2DPetView.ModelSource.Asset(model.modelJsonPath)
        else
            Live2DPetView.ModelSource.External(java.io.File(model.modelJsonPath))
        live2dView.switchModel(source)
        // 水色小狗永久去水印（Param121 Add 30）
        if (model.id == "water_dog") {
            live2dView.postDelayed({ live2dView.setParamPersistent("Param121", 30f) }, 500)
        } else {
            live2dView.clearPersistentParams()
        }
        tvCurrentAction.text = "模型：${model.name}"

        // 填充表情 Chips
        chipGroupExp.removeAllViews()
        chipGroupMot.removeAllViews()
        activeExpName = null

        val hasExp = model.expressions.isNotEmpty()
        val hasMot = model.motions.isNotEmpty()

        tvExpHeader.visibility = if (hasExp) View.VISIBLE else View.GONE
        tvMotHeader.visibility = if (hasMot) View.VISIBLE else View.GONE
        tvNoAction.visibility  = if (!hasExp && !hasMot) View.VISIBLE else View.GONE

        model.expressions.forEach { expName ->
            val chip = Chip(this).apply {
                text = expName.removeSuffix(".exp3.json")
                isClickable = true
                isCheckable = true
                setOnClickListener {
                    if (activeExpName == expName) {
                        // 再次点击同一表情 → 取消
                        isChecked = false
                        activeExpName = null
                        tvCurrentAction.text = "模型：${currentModel?.name}"
                        live2dView.clearExpression()
                    } else {
                        // 切换到新表情，取消其他 chip 选中
                        for (i in 0 until chipGroupExp.childCount) {
                            (chipGroupExp.getChildAt(i) as? Chip)?.isChecked = false
                        }
                        isChecked = true
                        activeExpName = expName
                        tvCurrentAction.text = "表情：${text}"
                        live2dView.playExpression(expName)
                    }
                }
            }
            chipGroupExp.addView(chip)
        }

        model.motions.forEach { motName ->
            val chip = Chip(this).apply {
                text = motName.removeSuffix(".motion3.json")
                isClickable = true
                isCheckable = true
                setOnClickListener {
                    tvCurrentAction.text = "动作：${text}"
                    live2dView.playMotionFile(motName)
                }
            }
            chipGroupMot.addView(chip)
        }
    }

    override fun onResume() {
        super.onResume()
        try { live2dView.onResume() } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        try { live2dView.onPause() } catch (_: Exception) {}
    }
}

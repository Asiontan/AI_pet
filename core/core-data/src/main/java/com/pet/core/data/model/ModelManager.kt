package com.pet.core.data.model

import android.content.Context
import android.content.SharedPreferences
import com.pet.core.common.logger.PetLogger
import java.io.File
import java.io.InputStream

/**
 * Live2D 模型管理器。
 *
 * - 内置模型：存放在 assets 目录，通过相对路径引用
 * - 用户模型：存放在 app 私有目录 files/live2d_models/ 下
 */
object ModelManager {

    private const val TAG = "ModelManager"
    private const val PREFS_NAME = "model_prefs"
    private const val KEY_ACTIVE_MODEL = "active_model_id"
    private const val USER_MODEL_DIR = "live2d_models"

    // ----------------------------------------------------------------
    // 数据结构
    // ----------------------------------------------------------------

    data class ModelInfo(
        val id: String,
        val name: String,
        val isBuiltin: Boolean,
        /** assets 内置模型的 model3.json 相对路径，或外部模型的绝对路径 */
        val modelJsonPath: String,
        /** assets 内置模型的目录（相对 assets），外部模型为空 */
        val assetBaseDir: String? = null,
        /** 预览缩略图路径（可选），外部模型目录下的 preview.png */
        val previewImagePath: String? = null,
        /** 表情文件名列表（含 .exp3.json 后缀，相对模型目录） */
        val expressions: List<String> = emptyList(),
        /** 动作文件名列表（含 .motion3.json 后缀，相对模型目录） */
        val motions: List<String> = emptyList()
    )

    // ----------------------------------------------------------------
    // 内置模型列表（在此处手动注册 assets 中的模型）
    // ----------------------------------------------------------------

    private val builtinModelDefs = listOf(
        Triple("cecilia_v4",  "Cecilia V4",   "ceclila_VTS/Cecilia_V4.model3.json"),
        Triple("cloud_girl",  "甜兔",          "cloudGird/甜兔.model3.json"),
        Triple("emoxiaomao",  "豪华套餐猫猫",   "emoxiaomao/豪华套餐猫猫.model3.json"),
        Triple("water_dog",   "水色小狗",      "waterDog/水色小狗.model3.json")
    )

    /**
     * 构建内置模型列表，扫描 assets 获取表情/动作文件。
     * 结果按需缓存，避免重复 IO。
     */
    private var builtinModelsCache: List<ModelInfo>? = null

    private fun getBuiltinModels(context: Context): List<ModelInfo> {
        builtinModelsCache?.let { return it }
        val result = builtinModelDefs.map { (id, name, jsonPath) ->
            val baseDir = jsonPath.substringBeforeLast('/')
            val (exps, mots) = scanAssetModelFiles(context, baseDir)
            ModelInfo(
                id = id,
                name = name,
                isBuiltin = true,
                modelJsonPath = jsonPath,
                assetBaseDir = baseDir,
                expressions = exps,
                motions = mots
            )
        }
        builtinModelsCache = result
        return result
    }

    /**
     * 扫描 assets 中模型目录，返回 (表情文件名列表, 动作文件名列表)。
     * 文件名为相对模型目录的相对路径（不含目录前缀）。
     */
    private fun scanAssetModelFiles(context: Context, baseDir: String): Pair<List<String>, List<String>> {
        return try {
            val files = context.assets.list(baseDir) ?: emptyArray()
            val exps  = files.filter { it.endsWith(".exp3.json") }
            val mots  = files.filter { it.endsWith(".motion3.json") }
            Pair(exps, mots)
        } catch (e: Exception) {
            PetLogger.e(TAG, "scanAssetModelFiles failed: $baseDir", e)
            Pair(emptyList(), emptyList())
        }
    }

    // ----------------------------------------------------------------
    // 公开 API
    // ----------------------------------------------------------------

    /** 获取所有模型（内置 + 用户上传） */
    fun getAllModels(context: Context): List<ModelInfo> {
        return getBuiltinModels(context) + getUserModels(context)
    }

    /** 获取用户上传的模型列表 */
    fun getUserModels(context: Context): List<ModelInfo> {
        val dir = getUserModelDir(context)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { modelDir ->
                val jsonFile = modelDir.listFiles()
                    ?.firstOrNull { it.name.endsWith(".model3.json") }
                    ?: return@mapNotNull null
                val preview = File(modelDir, "preview.png")
                    .takeIf { it.exists() }?.absolutePath
                val files = modelDir.listFiles() ?: emptyArray()
                val exps  = files.filter { it.name.endsWith(".exp3.json") }.map { it.name }
                val mots  = files.filter { it.name.endsWith(".motion3.json") }.map { it.name }
                ModelInfo(
                    id = "user_${modelDir.name}",
                    name = modelDir.name,
                    isBuiltin = false,
                    modelJsonPath = jsonFile.absolutePath,
                    previewImagePath = preview,
                    expressions = exps,
                    motions = mots
                )
            } ?: emptyList()
    }

    /** 获取当前激活的模型 ID，默认返回第一个内置模型 */
    fun getActiveModelId(context: Context): String {
        return prefs(context).getString(KEY_ACTIVE_MODEL, builtinModelDefs.first().first)
            ?: builtinModelDefs.first().first
    }

    /** 设置当前激活模型 */
    fun setActiveModel(context: Context, modelId: String) {
        prefs(context).edit().putString(KEY_ACTIVE_MODEL, modelId).apply()
    }

    /** 根据 ID 查找模型 */
    fun findModel(context: Context, modelId: String): ModelInfo? {
        return getAllModels(context).find { it.id == modelId }
    }

    /**
     * 导入用户模型。
     *
     * 将 zip 压缩包解压到私有目录，zip 内应包含：
     *   modelName/
     *     *.model3.json
     *     *.moc3
     *     *.png (纹理)
     *     (可选) preview.png
     *
     * @param context
     * @param zipStream  zip 文件的输入流
     * @param modelName  解压后的目录名（模型名称）
     * @return 导入成功后的 ModelInfo，失败返回 null
     */
    fun importModelFromZip(
        context: Context,
        zipStream: InputStream,
        modelName: String
    ): ModelInfo? {
        return try {
            val targetDir = File(getUserModelDir(context), sanitizeName(modelName))
            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()

            java.util.zip.ZipInputStream(zipStream.buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        // 去掉 zip 内的顶层目录前缀，直接写到 targetDir
                        val relativeName = entry.name
                            .substringAfter('/')
                            .ifBlank { entry.name }
                        val outFile = File(targetDir, relativeName)
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { zis.copyTo(it) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // 找 model3.json
            val jsonFile = targetDir.walkTopDown()
                .firstOrNull { it.name.endsWith(".model3.json") }
                ?: run {
                    targetDir.deleteRecursively()
                    PetLogger.e(TAG, "No .model3.json found in zip")
                    return null
                }

            val preview = File(targetDir, "preview.png").takeIf { it.exists() }?.absolutePath
            val files = targetDir.listFiles() ?: emptyArray()
            val exps  = files.filter { it.name.endsWith(".exp3.json") }.map { it.name }
            val mots  = files.filter { it.name.endsWith(".motion3.json") }.map { it.name }
            ModelInfo(
                id = "user_${targetDir.name}",
                name = targetDir.name,
                isBuiltin = false,
                modelJsonPath = jsonFile.absolutePath,
                previewImagePath = preview,
                expressions = exps,
                motions = mots
            )
        } catch (e: Exception) {
            PetLogger.e(TAG, "importModelFromZip failed", e)
            null
        }
    }

    /** 删除用户模型 */
    fun deleteUserModel(context: Context, modelId: String): Boolean {
        val model = findModel(context, modelId) ?: return false
        if (model.isBuiltin) return false
        return try {
            File(model.modelJsonPath).parentFile?.deleteRecursively() ?: false
        } catch (e: Exception) {
            PetLogger.e(TAG, "deleteUserModel failed", e)
            false
        }
    }

    /** 使内置模型缓存失效（一般不需要调用） */
    fun invalidateBuiltinCache() {
        builtinModelsCache = null
    }

    // ----------------------------------------------------------------
    // 内部工具
    // ----------------------------------------------------------------

    fun getUserModelDir(context: Context): File {
        return File(context.filesDir, USER_MODEL_DIR).also { it.mkdirs() }
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun sanitizeName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]"), "_")
    }
}

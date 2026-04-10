package com.pet.core.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.pet.core.common.constant.PetConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PetPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PetConstants.PREF_NAME,
        Context.MODE_PRIVATE
    )

    // ── 位置 ──────────────────────────────────────────────────────────
    suspend fun savePetX(x: Int) = withContext(Dispatchers.IO) {
        prefs.edit().putInt(PetConstants.PREF_KEY_PET_X, x).apply()
    }

    suspend fun getPetX(): Int = withContext(Dispatchers.IO) {
        prefs.getInt(PetConstants.PREF_KEY_PET_X, 0)
    }

    suspend fun savePetY(y: Int) = withContext(Dispatchers.IO) {
        prefs.edit().putInt(PetConstants.PREF_KEY_PET_Y, y).apply()
    }

    suspend fun getPetY(): Int = withContext(Dispatchers.IO) {
        prefs.getInt(PetConstants.PREF_KEY_PET_Y, 0)
    }

    // ── 情绪值 ────────────────────────────────────────────────────────
    /** 保存宠物当前情绪值（0-10） */
    fun savePetEmotion(emotion: Int) {
        prefs.edit().putInt(KEY_PET_EMOTION, emotion.coerceIn(0, 10)).apply()
    }

    /** 读取宠物情绪值，默认 5（中性） */
    fun getPetEmotion(): Int = prefs.getInt(KEY_PET_EMOTION, 5)

    // ── 点击统计 ──────────────────────────────────────────────────────
    /** 保存今日点击次数 */
    fun saveTodayClickCount(count: Int) {
        prefs.edit()
            .putInt(KEY_TODAY_CLICK_COUNT, count)
            .putLong(KEY_CLICK_COUNT_DATE, todayStartMs())
            .apply()
    }

    /** 读取今日点击次数（跨天自动清零） */
    fun getTodayClickCount(): Int {
        val savedDate = prefs.getLong(KEY_CLICK_COUNT_DATE, 0L)
        return if (savedDate == todayStartMs()) {
            prefs.getInt(KEY_TODAY_CLICK_COUNT, 0)
        } else {
            0 // 跨天清零
        }
    }

    // ── 最后交互时间 ───────────────────────────────────────────────────
    fun saveLastInteractionTime(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_INTERACTION_TIME, timestamp).apply()
    }

    fun getLastInteractionTime(): Long =
        prefs.getLong(KEY_LAST_INTERACTION_TIME, System.currentTimeMillis())

    // ── 边缘吸附 ──────────────────────────────────────────────────────
    suspend fun saveEdgeSnapped(isSnapped: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_EDGE_SNAPPED, isSnapped).apply()
    }

    suspend fun getEdgeSnapped(): Boolean = withContext(Dispatchers.IO) {
        prefs.getBoolean(KEY_EDGE_SNAPPED, false)
    }

    // ── 亲密度 ────────────────────────────────────────────────────────
    /**
     * 亲密度（0-100）：每次点击/聊天互动 +1，每天离线 -2（最低 0）
     * 用于解锁特殊互动动作、改变宠物表情倾向
     */
    fun saveBondLevel(level: Int) {
        prefs.edit().putInt(KEY_BOND_LEVEL, level.coerceIn(0, 100)).apply()
    }

    fun getBondLevel(): Int = prefs.getInt(KEY_BOND_LEVEL, 0)

    /** 亲密度增加（+delta），不超过上限 100 */
    fun addBond(delta: Int) {
        val cur = getBondLevel()
        saveBondLevel((cur + delta).coerceIn(0, 100))
    }

    // ── 总在线互动时长（分钟）────────────────────────────────────────
    /** 累计与宠物互动的总分钟数（每次启动服务时按实际运行时长追加） */
    fun addOnlineMinutes(minutes: Long) {
        val cur = prefs.getLong(KEY_TOTAL_ONLINE_MINUTES, 0L)
        prefs.edit().putLong(KEY_TOTAL_ONLINE_MINUTES, cur + minutes).apply()
    }

    fun getTotalOnlineMinutes(): Long = prefs.getLong(KEY_TOTAL_ONLINE_MINUTES, 0L)

    // ── 服务启动时间戳（用于计算在线时长）────────────────────────────
    fun saveServiceStartTime(ts: Long) {
        prefs.edit().putLong(KEY_SERVICE_START_TIME, ts).apply()
    }

    fun getServiceStartTime(): Long = prefs.getLong(KEY_SERVICE_START_TIME, 0L)

    // ── 累计聊天次数 ──────────────────────────────────────────────────
    fun incrementChatCount() {
        val cur = prefs.getInt(KEY_TOTAL_CHAT_COUNT, 0)
        prefs.edit().putInt(KEY_TOTAL_CHAT_COUNT, cur + 1).apply()
    }

    fun getTotalChatCount(): Int = prefs.getInt(KEY_TOTAL_CHAT_COUNT, 0)

    // ── 上次发送关心通知时间 ──────────────────────────────────────────
    fun saveLastCareNotifyTime(ts: Long) {
        prefs.edit().putLong(KEY_LAST_CARE_NOTIFY_TIME, ts).apply()
    }

    fun getLastCareNotifyTime(): Long = prefs.getLong(KEY_LAST_CARE_NOTIFY_TIME, 0L)

    // ── 手势识别开关 ──────────────────────────────────────────────────
    fun setGestureRecognitionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GESTURE_RECOGNITION_ENABLED, enabled).apply()
    }

    fun isGestureRecognitionEnabled(): Boolean =
        prefs.getBoolean(KEY_GESTURE_RECOGNITION_ENABLED, false)

    // ── 自定义系统提示词 ──────────────────────────────────────────────
    fun saveCustomSystemPrompt(prompt: String) {
        prefs.edit().putString(KEY_CUSTOM_SYSTEM_PROMPT, prompt).apply()
    }

    fun getCustomSystemPrompt(): String =
        prefs.getString(KEY_CUSTOM_SYSTEM_PROMPT, "") ?: ""

    // ── 清除 ──────────────────────────────────────────────────────────
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }

    // ── 私有工具 ──────────────────────────────────────────────────────
    private fun todayStartMs(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    companion object {
        private const val KEY_PET_EMOTION            = "pet_emotion"
        private const val KEY_TODAY_CLICK_COUNT       = "today_click_count"
        private const val KEY_CLICK_COUNT_DATE        = "click_count_date"
        private const val KEY_LAST_INTERACTION_TIME   = "last_interaction_time"
        private const val KEY_EDGE_SNAPPED            = "edge_snapped"
        private const val KEY_BOND_LEVEL              = "bond_level"
        private const val KEY_TOTAL_ONLINE_MINUTES    = "total_online_minutes"
        private const val KEY_SERVICE_START_TIME      = "service_start_time"
        private const val KEY_TOTAL_CHAT_COUNT        = "total_chat_count"
        private const val KEY_LAST_CARE_NOTIFY_TIME   = "last_care_notify_time"
        private const val KEY_CUSTOM_SYSTEM_PROMPT    = "custom_system_prompt"
        private const val KEY_GESTURE_RECOGNITION_ENABLED = "gesture_recognition_enabled"
    }
}

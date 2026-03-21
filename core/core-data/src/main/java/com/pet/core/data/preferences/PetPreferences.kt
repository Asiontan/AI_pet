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
        private const val KEY_PET_EMOTION           = "pet_emotion"
        private const val KEY_TODAY_CLICK_COUNT      = "today_click_count"
        private const val KEY_CLICK_COUNT_DATE       = "click_count_date"
        private const val KEY_LAST_INTERACTION_TIME  = "last_interaction_time"
        private const val KEY_EDGE_SNAPPED           = "edge_snapped"
    }
}

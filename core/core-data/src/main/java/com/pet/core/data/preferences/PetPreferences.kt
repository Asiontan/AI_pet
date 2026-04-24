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

    // ── 陪伴系统 2.0：关系与记忆 ───────────────────────────────────
    fun saveFamiliarityDays(days: Int) {
        prefs.edit().putInt(KEY_FAMILIARITY_DAYS, days.coerceAtLeast(0)).apply()
    }

    fun getFamiliarityDays(): Int = prefs.getInt(KEY_FAMILIARITY_DAYS, 0)

    fun incrementFamiliarityDayIfNeeded(now: Long = System.currentTimeMillis()) {
        val dayId = dayId(now)
        val lastDay = prefs.getLong(KEY_LAST_FAMILIARITY_DAY_ID, -1L)
        if (lastDay != dayId) {
            saveFamiliarityDays(getFamiliarityDays() + 1)
            prefs.edit().putLong(KEY_LAST_FAMILIARITY_DAY_ID, dayId).apply()
        }
    }

    fun saveRecentInteractionScore(score: Int) {
        prefs.edit().putInt(KEY_RECENT_INTERACTION_SCORE, score.coerceIn(0, 100)).apply()
    }

    fun getRecentInteractionScore(): Int = prefs.getInt(KEY_RECENT_INTERACTION_SCORE, 50)

    fun addRecentInteractionScore(delta: Int) {
        saveRecentInteractionScore(getRecentInteractionScore() + delta)
    }

    fun saveAcceptanceLevel(level: Int) {
        prefs.edit().putInt(KEY_ACCEPTANCE_LEVEL, level.coerceIn(0, 100)).apply()
    }

    fun getAcceptanceLevel(): Int = prefs.getInt(KEY_ACCEPTANCE_LEVEL, 50)

    fun addAcceptanceLevel(delta: Int) {
        saveAcceptanceLevel(getAcceptanceLevel() + delta)
    }

    fun saveNeglectLevel(level: Int) {
        prefs.edit().putInt(KEY_NEGLECT_LEVEL, level.coerceIn(0, 100)).apply()
    }

    fun getNeglectLevel(): Int = prefs.getInt(KEY_NEGLECT_LEVEL, 0)

    fun addNeglectLevel(delta: Int) {
        saveNeglectLevel(getNeglectLevel() + delta)
    }

    fun savePetLoneliness(level: Int) {
        prefs.edit().putInt(KEY_PET_LONELINESS, level.coerceIn(0, 100)).apply()
    }

    fun getPetLoneliness(): Int = prefs.getInt(KEY_PET_LONELINESS, 0)

    fun savePetTrust(level: Int) {
        prefs.edit().putInt(KEY_PET_TRUST, level.coerceIn(0, 100)).apply()
    }

    fun getPetTrust(): Int = prefs.getInt(KEY_PET_TRUST, 50)

    fun savePetEnergy(level: Int) {
        prefs.edit().putInt(KEY_PET_ENERGY, level.coerceIn(0, 100)).apply()
    }

    fun getPetEnergy(): Int = prefs.getInt(KEY_PET_ENERGY, 70)

    fun savePetMood(name: String) {
        prefs.edit().putString(KEY_PET_MOOD, name).apply()
    }

    fun getPetMood(): String = prefs.getString(KEY_PET_MOOD, "CALM") ?: "CALM"

    fun saveLastUserMood(name: String) {
        prefs.edit().putString(KEY_LAST_USER_MOOD, name).apply()
    }

    fun getLastUserMood(): String = prefs.getString(KEY_LAST_USER_MOOD, "UNKNOWN") ?: "UNKNOWN"

    fun saveLastCompanionMode(name: String) {
        prefs.edit().putString(KEY_LAST_COMPANION_MODE, name).apply()
    }

    fun getLastCompanionMode(): String = prefs.getString(KEY_LAST_COMPANION_MODE, "QUIET_COMPANION") ?: "QUIET_COMPANION"

    fun incrementInteractionDayIfNeeded(now: Long = System.currentTimeMillis()) {
        val dayId = dayId(now)
        val lastDay = prefs.getLong(KEY_LAST_INTERACTION_DAY_ID, -1L)
        val streak = prefs.getInt(KEY_INTERACTION_STREAK_DAYS, 0)
        if (lastDay == dayId) return
        val newStreak = if (lastDay == dayId - 1) streak + 1 else 1
        prefs.edit()
            .putLong(KEY_LAST_INTERACTION_DAY_ID, dayId)
            .putInt(KEY_INTERACTION_STREAK_DAYS, newStreak)
            .apply()
    }

    fun getInteractionStreakDays(): Int = prefs.getInt(KEY_INTERACTION_STREAK_DAYS, 0)

    fun incrementDailyInteractionCount() {
        val today = dayId(System.currentTimeMillis())
        val savedDay = prefs.getLong(KEY_DAILY_INTERACTION_DAY_ID, -1L)
        val current = if (savedDay == today) prefs.getInt(KEY_DAILY_INTERACTION_COUNT, 0) else 0
        prefs.edit()
            .putLong(KEY_DAILY_INTERACTION_DAY_ID, today)
            .putInt(KEY_DAILY_INTERACTION_COUNT, current + 1)
            .apply()
    }

    fun getDailyInteractionCount(): Int {
        val today = dayId(System.currentTimeMillis())
        val savedDay = prefs.getLong(KEY_DAILY_INTERACTION_DAY_ID, -1L)
        return if (savedDay == today) prefs.getInt(KEY_DAILY_INTERACTION_COUNT, 0) else 0
    }

    fun saveLastInteractionHour(hour: Int) {
        prefs.edit().putInt(KEY_LAST_INTERACTION_HOUR, hour.coerceIn(0, 23)).apply()
    }

    fun getLastInteractionHour(): Int = prefs.getInt(KEY_LAST_INTERACTION_HOUR, -1)

    fun incrementLateNightInteractionCount() {
        val cur = prefs.getInt(KEY_LATE_NIGHT_INTERACTION_COUNT, 0)
        prefs.edit().putInt(KEY_LATE_NIGHT_INTERACTION_COUNT, cur + 1).apply()
    }

    fun getLateNightInteractionCount(): Int = prefs.getInt(KEY_LATE_NIGHT_INTERACTION_COUNT, 0)

    fun incrementIgnoredCareCount() {
        val cur = prefs.getInt(KEY_IGNORED_CARE_COUNT, 0)
        prefs.edit().putInt(KEY_IGNORED_CARE_COUNT, cur + 1).apply()
    }

    fun getIgnoredCareCount(): Int = prefs.getInt(KEY_IGNORED_CARE_COUNT, 0)

    fun incrementMeaningfulConversationCount() {
        val cur = prefs.getInt(KEY_MEANINGFUL_CONVERSATION_COUNT, 0)
        prefs.edit().putInt(KEY_MEANINGFUL_CONVERSATION_COUNT, cur + 1).apply()
    }

    fun getMeaningfulConversationCount(): Int = prefs.getInt(KEY_MEANINGFUL_CONVERSATION_COUNT, 0)

    fun incrementPassiveDayCount() {
        val cur = prefs.getInt(KEY_PASSIVE_DAY_COUNT, 0)
        prefs.edit().putInt(KEY_PASSIVE_DAY_COUNT, cur + 1).apply()
    }

    fun getPassiveDayCount(): Int = prefs.getInt(KEY_PASSIVE_DAY_COUNT, 0)

    fun saveModeAffinity(mode: String, value: Int) {
        prefs.edit().putInt(KEY_MODE_AFFINITY_PREFIX + mode, value.coerceIn(0, 100)).apply()
    }

    fun getModeAffinity(mode: String): Int = prefs.getInt(KEY_MODE_AFFINITY_PREFIX + mode, 50)

    fun addModeAffinity(mode: String, delta: Int) {
        saveModeAffinity(mode, getModeAffinity(mode) + delta)
    }

    fun updatePreferredInteractionHour(hour: Int) {
        prefs.edit().putInt(KEY_PREFERRED_INTERACTION_HOUR, hour.coerceIn(0, 23)).apply()
    }

    fun getPreferredInteractionHour(): Int = prefs.getInt(KEY_PREFERRED_INTERACTION_HOUR, -1)

    fun saveEpisodeMode(mode: String) {
        prefs.edit().putString(KEY_EPISODE_MODE, mode).apply()
    }

    fun getEpisodeMode(): String = prefs.getString(KEY_EPISODE_MODE, "") ?: ""

    fun saveEpisodeUserMood(mood: String) {
        prefs.edit().putString(KEY_EPISODE_USER_MOOD, mood).apply()
    }

    fun getEpisodeUserMood(): String = prefs.getString(KEY_EPISODE_USER_MOOD, "") ?: ""

    fun saveEpisodeStartTime(ts: Long) {
        prefs.edit().putLong(KEY_EPISODE_START_TIME, ts).apply()
    }

    fun getEpisodeStartTime(): Long = prefs.getLong(KEY_EPISODE_START_TIME, 0L)

    fun saveEpisodeIntimacy(value: Int) {
        prefs.edit().putInt(KEY_EPISODE_INTIMACY, value.coerceIn(0, 100)).apply()
    }

    fun getEpisodeIntimacy(): Int = prefs.getInt(KEY_EPISODE_INTIMACY, 0)

    fun saveEpisodeOutcome(outcome: String) {
        prefs.edit().putString(KEY_EPISODE_OUTCOME, outcome).apply()
    }

    fun getEpisodeOutcome(): String = prefs.getString(KEY_EPISODE_OUTCOME, "") ?: ""

    fun saveEpisodeDurationMinutes(value: Int) {
        prefs.edit().putInt(KEY_EPISODE_DURATION_MINUTES, value.coerceAtLeast(0)).apply()
    }

    fun getEpisodeDurationMinutes(): Int = prefs.getInt(KEY_EPISODE_DURATION_MINUTES, 0)

    fun saveEpisodeActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_EPISODE_ACTIVE, active).apply()
    }

    fun isEpisodeActive(): Boolean = prefs.getBoolean(KEY_EPISODE_ACTIVE, false)

    fun incrementEpisodeCount() {
        val cur = prefs.getInt(KEY_EPISODE_COUNT, 0)
        prefs.edit().putInt(KEY_EPISODE_COUNT, cur + 1).apply()
    }

    fun getEpisodeCount(): Int = prefs.getInt(KEY_EPISODE_COUNT, 0)

    fun incrementPositiveEpisodeCount() {
        val cur = prefs.getInt(KEY_POSITIVE_EPISODE_COUNT, 0)
        prefs.edit().putInt(KEY_POSITIVE_EPISODE_COUNT, cur + 1).apply()
    }

    fun getPositiveEpisodeCount(): Int = prefs.getInt(KEY_POSITIVE_EPISODE_COUNT, 0)

    fun incrementNegativeEpisodeCount() {
        val cur = prefs.getInt(KEY_NEGATIVE_EPISODE_COUNT, 0)
        prefs.edit().putInt(KEY_NEGATIVE_EPISODE_COUNT, cur + 1).apply()
    }

    fun getNegativeEpisodeCount(): Int = prefs.getInt(KEY_NEGATIVE_EPISODE_COUNT, 0)

    fun addEpisodeReward(mode: String, delta: Int) {
        val key = KEY_EPISODE_REWARD_PREFIX + mode
        val cur = prefs.getInt(key, 0)
        prefs.edit().putInt(key, cur + delta).apply()
    }

    fun getEpisodeReward(mode: String): Int = prefs.getInt(KEY_EPISODE_REWARD_PREFIX + mode, 0)

    fun saveLastPolicyContextKey(key: String) {
        prefs.edit().putString(KEY_LAST_POLICY_CONTEXT_KEY, key).apply()
    }

    fun getLastPolicyContextKey(): String = prefs.getString(KEY_LAST_POLICY_CONTEXT_KEY, "") ?: ""

    fun addContextOutcomeScore(key: String, delta: Int) {
        val storageKey = KEY_CONTEXT_OUTCOME_PREFIX + key
        val cur = prefs.getInt(storageKey, 0)
        prefs.edit().putInt(storageKey, cur + delta).apply()
    }

    fun getContextOutcomeScore(key: String): Int = prefs.getInt(KEY_CONTEXT_OUTCOME_PREFIX + key, 0)

    fun incrementContextPositiveCount(key: String) {
        val storageKey = KEY_CONTEXT_POSITIVE_PREFIX + key
        val cur = prefs.getInt(storageKey, 0)
        prefs.edit().putInt(storageKey, cur + 1).apply()
    }

    fun getContextPositiveCount(key: String): Int = prefs.getInt(KEY_CONTEXT_POSITIVE_PREFIX + key, 0)

    fun incrementContextNegativeCount(key: String) {
        val storageKey = KEY_CONTEXT_NEGATIVE_PREFIX + key
        val cur = prefs.getInt(storageKey, 0)
        prefs.edit().putInt(storageKey, cur + 1).apply()
    }

    fun getContextNegativeCount(key: String): Int = prefs.getInt(KEY_CONTEXT_NEGATIVE_PREFIX + key, 0)

    fun saveCompanionStyleMode(mode: String) {
        prefs.edit().putString(KEY_COMPANION_STYLE_MODE, mode).apply()
    }

    fun getCompanionStyleMode(): String = prefs.getString(KEY_COMPANION_STYLE_MODE, "") ?: ""

    fun saveStyleWeight(style: String, value: Int) {
        prefs.edit().putInt(KEY_STYLE_WEIGHT_PREFIX + style, value.coerceIn(-100, 100)).apply()
    }

    fun getStyleWeight(style: String): Int = prefs.getInt(KEY_STYLE_WEIGHT_PREFIX + style, 0)

    fun addStyleWeight(style: String, delta: Int) {
        saveStyleWeight(style, getStyleWeight(style) + delta)
    }

    fun saveRelationshipPhase(phase: String) {
        prefs.edit().putString(KEY_RELATIONSHIP_PHASE, phase).apply()
    }

    fun getRelationshipPhase(): String = prefs.getString(KEY_RELATIONSHIP_PHASE, "") ?: ""

    // ── 上次发送关心通知时间 ──────────────────────────────────────────
    fun saveLastCareNotifyTime(ts: Long) {
        prefs.edit().putLong(KEY_LAST_CARE_NOTIFY_TIME, ts).apply()
    }

    fun getLastCareNotifyTime(): Long = prefs.getLong(KEY_LAST_CARE_NOTIFY_TIME, 0L)

    // ── 休闲场景（刷视频/玩游戏）气泡冷却 ───────────────────────────────
    fun saveLastEntertainmentBubbleTime(ts: Long) {
        prefs.edit().putLong(KEY_LAST_ENTERTAINMENT_BUBBLE_TIME, ts).apply()
    }

    fun getLastEntertainmentBubbleTime(): Long =
        prefs.getLong(KEY_LAST_ENTERTAINMENT_BUBBLE_TIME, 0L)

    // ── 全局气泡去重（避免连续两次同一句）──────────────────────────────
    fun saveLastGlobalBubbleText(text: String) {
        prefs.edit().putString(KEY_LAST_GLOBAL_BUBBLE_TEXT, text).apply()
    }

    fun getLastGlobalBubbleText(): String =
        prefs.getString(KEY_LAST_GLOBAL_BUBBLE_TEXT, "") ?: ""

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

    // ── Bmob 云端登录态 ──────────────────────────────────────────────
    fun saveBmobSessionToken(token: String) {
        prefs.edit().putString(KEY_BMOB_SESSION_TOKEN, token).apply()
    }

    fun getBmobSessionToken(): String =
        prefs.getString(KEY_BMOB_SESSION_TOKEN, "") ?: ""

    fun saveBmobUserId(objectId: String) {
        prefs.edit().putString(KEY_BMOB_USER_ID, objectId).apply()
    }

    fun getBmobUserId(): String =
        prefs.getString(KEY_BMOB_USER_ID, "") ?: ""

    fun saveBmobUsername(username: String) {
        prefs.edit().putString(KEY_BMOB_USERNAME, username).apply()
    }

    fun getBmobUsername(): String =
        prefs.getString(KEY_BMOB_USERNAME, "") ?: ""

    /** 云端宠物数据的 objectId，用于增量更新 */
    fun saveBmobPetDataId(objectId: String) {
        prefs.edit().putString(KEY_BMOB_PET_DATA_ID, objectId).apply()
    }

    fun getBmobPetDataId(): String =
        prefs.getString(KEY_BMOB_PET_DATA_ID, "") ?: ""

    fun clearBmobSession() {
        prefs.edit()
            .remove(KEY_BMOB_SESSION_TOKEN)
            .remove(KEY_BMOB_USER_ID)
            .remove(KEY_BMOB_USERNAME)
            .remove(KEY_BMOB_PET_DATA_ID)
            .apply()
    }

    fun isBmobLoggedIn(): Boolean = getBmobSessionToken().isNotEmpty()

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

    private fun dayId(timestamp: Long): Long = timestamp / (24L * 60L * 60L * 1000L)

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
        private const val KEY_FAMILIARITY_DAYS        = "familiarity_days"
        private const val KEY_LAST_FAMILIARITY_DAY_ID = "last_familiarity_day_id"
        private const val KEY_RECENT_INTERACTION_SCORE = "recent_interaction_score"
        private const val KEY_ACCEPTANCE_LEVEL        = "acceptance_level"
        private const val KEY_NEGLECT_LEVEL           = "neglect_level"
        private const val KEY_PET_LONELINESS          = "pet_loneliness"
        private const val KEY_PET_TRUST               = "pet_trust"
        private const val KEY_PET_ENERGY              = "pet_energy"
        private const val KEY_PET_MOOD                = "pet_mood"
        private const val KEY_LAST_USER_MOOD          = "last_user_mood"
        private const val KEY_LAST_COMPANION_MODE     = "last_companion_mode"
        private const val KEY_LAST_INTERACTION_DAY_ID = "last_interaction_day_id"
        private const val KEY_INTERACTION_STREAK_DAYS = "interaction_streak_days"
        private const val KEY_DAILY_INTERACTION_DAY_ID = "daily_interaction_day_id"
        private const val KEY_DAILY_INTERACTION_COUNT = "daily_interaction_count"
        private const val KEY_LAST_INTERACTION_HOUR   = "last_interaction_hour"
        private const val KEY_LATE_NIGHT_INTERACTION_COUNT = "late_night_interaction_count"
        private const val KEY_IGNORED_CARE_COUNT      = "ignored_care_count"
        private const val KEY_MEANINGFUL_CONVERSATION_COUNT = "meaningful_conversation_count"
        private const val KEY_PASSIVE_DAY_COUNT       = "passive_day_count"
        private const val KEY_MODE_AFFINITY_PREFIX   = "mode_affinity_"
        private const val KEY_PREFERRED_INTERACTION_HOUR = "preferred_interaction_hour"
        private const val KEY_EPISODE_MODE           = "episode_mode"
        private const val KEY_EPISODE_USER_MOOD      = "episode_user_mood"
        private const val KEY_EPISODE_START_TIME     = "episode_start_time"
        private const val KEY_EPISODE_INTIMACY       = "episode_intimacy"
        private const val KEY_EPISODE_OUTCOME        = "episode_outcome"
        private const val KEY_EPISODE_DURATION_MINUTES = "episode_duration_minutes"
        private const val KEY_EPISODE_ACTIVE         = "episode_active"
        private const val KEY_EPISODE_COUNT          = "episode_count"
        private const val KEY_POSITIVE_EPISODE_COUNT = "positive_episode_count"
        private const val KEY_NEGATIVE_EPISODE_COUNT = "negative_episode_count"
        private const val KEY_EPISODE_REWARD_PREFIX  = "episode_reward_"
        private const val KEY_LAST_POLICY_CONTEXT_KEY = "last_policy_context_key"
        private const val KEY_CONTEXT_OUTCOME_PREFIX  = "context_outcome_"
        private const val KEY_CONTEXT_POSITIVE_PREFIX = "context_positive_"
        private const val KEY_CONTEXT_NEGATIVE_PREFIX = "context_negative_"
        private const val KEY_COMPANION_STYLE_MODE    = "companion_style_mode"
        private const val KEY_STYLE_WEIGHT_PREFIX     = "style_weight_"
        private const val KEY_RELATIONSHIP_PHASE      = "relationship_phase"
        private const val KEY_LAST_CARE_NOTIFY_TIME   = "last_care_notify_time"
        private const val KEY_LAST_ENTERTAINMENT_BUBBLE_TIME = "last_entertainment_bubble_time"
        private const val KEY_LAST_GLOBAL_BUBBLE_TEXT = "last_global_bubble_text"
        private const val KEY_CUSTOM_SYSTEM_PROMPT    = "custom_system_prompt"
        private const val KEY_GESTURE_RECOGNITION_ENABLED = "gesture_recognition_enabled"
        private const val KEY_BMOB_SESSION_TOKEN    = "bmob_session_token"
        private const val KEY_BMOB_USER_ID          = "bmob_user_id"
        private const val KEY_BMOB_USERNAME          = "bmob_username"
        private const val KEY_BMOB_PET_DATA_ID      = "bmob_pet_data_id"
    }
}

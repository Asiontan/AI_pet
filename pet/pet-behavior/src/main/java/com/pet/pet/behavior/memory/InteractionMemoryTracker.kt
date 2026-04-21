package com.pet.pet.behavior.memory

import com.pet.core.data.preferences.PetPreferences

class InteractionMemoryTracker(
    private val preferences: PetPreferences
) {
    fun recordInteraction(timestamp: Long = System.currentTimeMillis()) {
        preferences.incrementInteractionDayIfNeeded(timestamp)
        preferences.incrementDailyInteractionCount()
        preferences.saveLastInteractionHour(hourOfDay(timestamp))
        if (isLateNight(timestamp)) {
            preferences.incrementLateNightInteractionCount()
        }
    }

    fun recordChatCompletion(timestamp: Long = System.currentTimeMillis()) {
        recordInteraction(timestamp)
        preferences.incrementMeaningfulConversationCount()
    }

    fun recordIgnoredCare() {
        preferences.incrementIgnoredCareCount()
    }

    fun recordPassiveDecay() {
        preferences.incrementPassiveDayCount()
    }

    private fun hourOfDay(timestamp: Long): Int {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        return cal.get(java.util.Calendar.HOUR_OF_DAY)
    }

    private fun isLateNight(timestamp: Long): Boolean {
        val hour = hourOfDay(timestamp)
        return hour >= 23 || hour < 6
    }
}

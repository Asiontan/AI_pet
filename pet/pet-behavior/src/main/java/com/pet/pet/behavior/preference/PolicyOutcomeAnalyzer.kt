package com.pet.pet.behavior.preference

import com.pet.core.data.preferences.PetPreferences
import com.pet.core.domain.model.CompanionMode
import com.pet.core.domain.model.CompanionPolicy
import com.pet.core.domain.model.InteractionAvailability
import com.pet.core.domain.model.LifeContext
import com.pet.core.domain.model.UserMood
import com.pet.core.domain.model.UserState
import kotlin.math.abs

class PolicyOutcomeAnalyzer(
    private val preferences: PetPreferences
) {
    fun recordCurrentContext(mode: String, userState: UserState) {
        val contextKey = contextKey(mode, userState)
        preferences.saveLastPolicyContextKey(contextKey)
    }

    fun recordOutcome(rewardDelta: Int) {
        val contextKey = preferences.getLastPolicyContextKey()
        if (contextKey.isBlank()) return
        preferences.addContextOutcomeScore(contextKey, rewardDelta)
        if (rewardDelta > 0) {
            preferences.incrementContextPositiveCount(contextKey)
        } else {
            preferences.incrementContextNegativeCount(contextKey)
        }
    }

    fun contextualBias(policy: CompanionPolicy, userState: UserState): Int {
        val contextKey = contextKey(policy.mode.name, userState)
        val score = preferences.getContextOutcomeScore(contextKey).coerceIn(-12, 12)
        val positive = preferences.getContextPositiveCount(contextKey)
        val negative = preferences.getContextNegativeCount(contextKey)
        val balance = when {
            positive + negative == 0 -> 0
            positive >= negative -> 4
            else -> -4
        }
        val hourBonus = preferences.getPreferredInteractionHour().let { hour ->
            if (hour in 0..23 && abs(hour - currentHour()) <= 2) 4 else 0
        }
        return score + balance + hourBonus
    }

    private fun contextKey(mode: String, userState: UserState): String {
        return listOf(mode, userState.mood.name, userState.lifeContext.name, userState.interactionAvailability.name).joinToString("|")
    }

    private fun currentHour(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
}

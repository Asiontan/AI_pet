package com.pet.pet.behavior.preference

import com.pet.core.data.preferences.PetPreferences
import com.pet.core.domain.model.CompanionMode
import com.pet.core.domain.model.CompanionPolicy
import com.pet.core.domain.model.InteractionAvailability
import com.pet.core.domain.model.UserState
import kotlin.math.abs

class AdaptivePolicyScorer(
    private val preferences: PetPreferences,
    private val policyOutcomeAnalyzer: PolicyOutcomeAnalyzer
) {
    fun score(
        basePolicy: CompanionPolicy,
        userState: UserState
    ): CompanionPolicy {
        val mode = basePolicy.mode.name
        val affinity = preferences.getModeAffinity(mode)
        val reward = preferences.getEpisodeReward(mode)
        val positive = preferences.getPositiveEpisodeCount()
        val negative = preferences.getNegativeEpisodeCount()
        val successBias = when {
            positive + negative == 0 -> 0
            positive >= negative -> 6
            else -> -6
        }
        val contextualBias = policyOutcomeAnalyzer.contextualBias(basePolicy, userState)
        val episodeBias = reward.coerceIn(-20, 20)
        val hourBias = preferences.getPreferredInteractionHour().let { hour ->
            if (hour in 0..23 && abs(hour - currentHour()) <= 2) 8 else 0
        }
        val availabilityPenalty = when (userState.interactionAvailability) {
            InteractionAvailability.OPEN -> 0
            InteractionAvailability.LOW -> 10
            InteractionAvailability.DO_NOT_DISTURB -> 20
        }

        val initiative = (basePolicy.initiativeLevel + affinity / 6 + episodeBias + contextualBias + successBias + hourBias - availabilityPenalty)
            .coerceIn(0, 100)
        val expressiveness = (basePolicy.expressivenessLevel + affinity / 8 + episodeBias / 2 + contextualBias + successBias - availabilityPenalty / 2)
            .coerceIn(0, 100)

        val adaptedMode = when {
            basePolicy.mode == CompanionMode.PROACTIVE_HELP && reward + contextualBias < -6 -> CompanionMode.QUIET_COMPANION
            basePolicy.mode == CompanionMode.PLAYFUL_INTERACTION && reward + contextualBias < -8 -> CompanionMode.QUIET_COMPANION
            basePolicy.mode == CompanionMode.GENTLE_CARE && reward + contextualBias > 8 -> CompanionMode.EMOTIONAL_SUPPORT
            else -> basePolicy.mode
        }

        return basePolicy.copy(
            mode = adaptedMode,
            initiativeLevel = initiative,
            expressivenessLevel = expressiveness
        )
    }

    private fun currentHour(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
}

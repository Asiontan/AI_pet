package com.pet.pet.behavior.expression

import com.pet.core.data.preferences.PetPreferences
import com.pet.core.domain.model.CompanionMode
import com.pet.core.domain.model.CompanionPolicy
import com.pet.core.domain.model.PetMindState
import com.pet.core.domain.model.RelationshipState
import com.pet.core.domain.model.UserMood
import com.pet.core.domain.model.UserState

class NonverbalInterventionPlanner(
    private val preferences: PetPreferences
) {
    data class NonverbalDecision(
        val triggerNonverbalOnly: Boolean,
        val motionDelayMs: Long
    )

    fun plan(
        userState: UserState,
        petMindState: PetMindState,
        relationshipState: RelationshipState,
        policy: CompanionPolicy,
        triggerBubble: Boolean
    ): NonverbalDecision {
        if (policy.mode == CompanionMode.DO_NOT_DISTURB) {
            return NonverbalDecision(false, 0L)
        }

        var quietBias = 0
        if (userState.mood in listOf(UserMood.SAD, UserMood.STRESSED)) quietBias += 14
        if (userState.mood == UserMood.TIRED) quietBias += 10
        if (relationshipState.intimacyLevel >= 60) quietBias += 6
        if (preferences.getIgnoredCareCount() >= 2) quietBias += 10
        if (petMindState.desireToInteract > 75) quietBias -= 6
        if (policy.mode == CompanionMode.QUIET_COMPANION || policy.mode == CompanionMode.EMOTIONAL_SUPPORT) quietBias += 8

        val shouldUseNonverbalOnly = !triggerBubble && quietBias >= 18
        val delay = when {
            !shouldUseNonverbalOnly -> 0L
            quietBias >= 28 -> 0L
            else -> 700L
        }

        return NonverbalDecision(
            triggerNonverbalOnly = shouldUseNonverbalOnly,
            motionDelayMs = delay
        )
    }
}

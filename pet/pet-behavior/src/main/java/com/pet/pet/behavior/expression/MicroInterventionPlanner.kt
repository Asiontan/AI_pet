package com.pet.pet.behavior.expression

import com.pet.core.data.preferences.PetPreferences
import com.pet.core.domain.model.CompanionMode
import com.pet.core.domain.model.CompanionPolicy
import com.pet.core.domain.model.PetMindState
import com.pet.core.domain.model.RelationshipState
import com.pet.core.domain.model.UserMood
import com.pet.core.domain.model.UserState

class MicroInterventionPlanner(
    private val preferences: PetPreferences
) {
    data class InterventionDecision(
        val shouldTriggerBubble: Boolean,
        val bubbleDelayMs: Long,
        val interventionLevel: Int
    )

    fun plan(
        userState: UserState,
        petMindState: PetMindState,
        relationshipState: RelationshipState,
        policy: CompanionPolicy,
        bubbleText: String?
    ): InterventionDecision {
        if (bubbleText.isNullOrBlank()) {
            return InterventionDecision(false, 0L, 0)
        }

        var level = when (policy.mode) {
            CompanionMode.EMOTIONAL_SUPPORT -> 28
            CompanionMode.GENTLE_CARE -> 34
            CompanionMode.PROACTIVE_HELP -> 42
            CompanionMode.PLAYFUL_INTERACTION -> 58
            CompanionMode.QUIET_COMPANION -> 12
            CompanionMode.DO_NOT_DISTURB -> 0
        }

        if (userState.mood in listOf(UserMood.SAD, UserMood.STRESSED)) level -= 8
        if (userState.mood == UserMood.HAPPY) level += 8
        if (relationshipState.intimacyLevel > 60) level += 6
        if (preferences.getIgnoredCareCount() >= 2) level -= 10
        if (petMindState.desireToInteract > 70) level += 6

        val finalLevel = level.coerceIn(0, 100)
        val shouldTrigger = finalLevel >= 18 && policy.mode != CompanionMode.DO_NOT_DISTURB
        val delay = when {
            !shouldTrigger -> 0L
            finalLevel < 28 -> 1800L
            finalLevel < 45 -> 900L
            else -> 0L
        }

        return InterventionDecision(
            shouldTriggerBubble = shouldTrigger,
            bubbleDelayMs = delay,
            interventionLevel = finalLevel
        )
    }
}

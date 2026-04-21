package com.pet.pet.behavior.expression

import com.pet.core.domain.model.CompanionMode
import com.pet.core.domain.model.CompanionPolicy
import com.pet.core.domain.model.PetMindState
import com.pet.core.domain.model.UserState

class InterventionSequencer {
    data class SequenceDecision(
        val expressionFirst: Boolean,
        val sequenceGapMs: Long
    )

    fun plan(
        userState: UserState,
        petMindState: PetMindState,
        policy: CompanionPolicy,
        triggerBubble: Boolean,
        triggerNonverbalOnly: Boolean
    ): SequenceDecision {
        if (triggerNonverbalOnly) {
            return SequenceDecision(expressionFirst = true, sequenceGapMs = 500L)
        }

        return when (policy.mode) {
            CompanionMode.EMOTIONAL_SUPPORT -> SequenceDecision(expressionFirst = true, sequenceGapMs = if (triggerBubble) 900L else 500L)
            CompanionMode.GENTLE_CARE -> SequenceDecision(expressionFirst = true, sequenceGapMs = if (userState.mood.name == "SAD") 800L else 600L)
            CompanionMode.PROACTIVE_HELP -> SequenceDecision(expressionFirst = false, sequenceGapMs = if (triggerBubble) 400L else 250L)
            CompanionMode.PLAYFUL_INTERACTION -> SequenceDecision(expressionFirst = false, sequenceGapMs = if (petMindState.desireToInteract > 70) 200L else 350L)
            CompanionMode.QUIET_COMPANION -> SequenceDecision(expressionFirst = true, sequenceGapMs = 700L)
            CompanionMode.DO_NOT_DISTURB -> SequenceDecision(expressionFirst = true, sequenceGapMs = 0L)
        }
    }
}

package com.pet.pet.behavior.policy

import com.pet.core.data.preferences.PetPreferences
import com.pet.core.domain.model.CompanionMode
import com.pet.core.domain.model.CompanionPolicy
import com.pet.core.domain.model.InteractionAvailability
import com.pet.core.domain.model.LifeContext
import com.pet.core.domain.model.PetMindState
import com.pet.core.domain.model.RelationshipState
import com.pet.core.domain.model.SupportType
import com.pet.core.domain.model.UserMood
import com.pet.core.domain.model.UserState

class ContextualPolicySelector(
    private val preferences: PetPreferences
) {
    fun select(
        basePolicy: CompanionPolicy,
        userState: UserState,
        petMindState: PetMindState,
        relationshipState: RelationshipState
    ): CompanionPolicy {
        val candidates = buildCandidates(basePolicy, userState, petMindState, relationshipState)
        return candidates.maxByOrNull { score(it, userState, petMindState, relationshipState) } ?: basePolicy
    }

    private fun buildCandidates(
        basePolicy: CompanionPolicy,
        userState: UserState,
        petMindState: PetMindState,
        relationshipState: RelationshipState
    ): List<CompanionPolicy> {
        val candidates = mutableListOf(basePolicy)
        if (userState.mood == UserMood.SAD || userState.mood == UserMood.STRESSED) {
            candidates += CompanionPolicy(CompanionMode.GENTLE_CARE, 35, 30, SupportType.LISTENING)
            if (relationshipState.intimacyLevel >= 55) {
                candidates += CompanionPolicy(CompanionMode.EMOTIONAL_SUPPORT, 38, 28, SupportType.ENCOURAGEMENT)
            }
        }
        if (userState.mood == UserMood.TIRED || userState.lifeContext == LifeContext.LATE_NIGHT) {
            candidates += CompanionPolicy(CompanionMode.PROACTIVE_HELP, 28, 25, SupportType.REST_REMINDER)
            candidates += CompanionPolicy(CompanionMode.QUIET_COMPANION, 18, 18, SupportType.NONE)
        }
        if ((userState.mood == UserMood.HAPPY || userState.mood == UserMood.CALM) && petMindState.desireToInteract > 55) {
            candidates += CompanionPolicy(CompanionMode.PLAYFUL_INTERACTION, 48, 68, SupportType.NONE)
        }
        if (userState.interactionAvailability == InteractionAvailability.LOW) {
            candidates += CompanionPolicy(CompanionMode.QUIET_COMPANION, 15, 16, SupportType.NONE)
        }
        return candidates.distinctBy { it.mode }
    }

    private fun score(
        policy: CompanionPolicy,
        userState: UserState,
        petMindState: PetMindState,
        relationshipState: RelationshipState
    ): Int {
        var score = 0
        score += preferences.getModeAffinity(policy.mode.name) / 4
        score += preferences.getEpisodeReward(policy.mode.name).coerceIn(-12, 12)
        if (userState.lifeContext == LifeContext.LATE_NIGHT && policy.mode == CompanionMode.QUIET_COMPANION) score += 14
        if (userState.lifeContext == LifeContext.LATE_NIGHT && policy.mode == CompanionMode.PROACTIVE_HELP) score += 8
        if ((userState.mood == UserMood.SAD || userState.mood == UserMood.STRESSED) && policy.mode == CompanionMode.GENTLE_CARE) score += 16
        if ((userState.mood == UserMood.SAD || userState.mood == UserMood.STRESSED) && policy.mode == CompanionMode.EMOTIONAL_SUPPORT) score += relationshipState.intimacyLevel / 6
        if (userState.mood == UserMood.TIRED && policy.mode == CompanionMode.PROACTIVE_HELP) score += 15
        if ((userState.mood == UserMood.HAPPY || userState.mood == UserMood.CALM) && policy.mode == CompanionMode.PLAYFUL_INTERACTION) {
            score += petMindState.desireToInteract / 5
        }
        if (userState.interactionAvailability == InteractionAvailability.LOW && policy.mode != CompanionMode.QUIET_COMPANION) score -= 10
        if (relationshipState.neglectLevel > 45 && policy.mode == CompanionMode.PLAYFUL_INTERACTION) score -= 8
        if (relationshipState.intimacyLevel > 60 && policy.mode == CompanionMode.EMOTIONAL_SUPPORT) score += 8
        return score
    }
}

package com.pet.pet.behavior.policy

import com.pet.core.domain.model.AttentionState
import com.pet.core.domain.model.CompanionMode
import com.pet.core.domain.model.CompanionPolicy
import com.pet.core.domain.model.InteractionAvailability
import com.pet.core.domain.model.PetMindState
import com.pet.core.domain.model.RelationshipState
import com.pet.core.domain.model.SupportType
import com.pet.core.domain.model.UserMood
import com.pet.core.domain.model.UserState

class CompanionPolicyEngine {
    fun decide(
        userState: UserState,
        petMindState: PetMindState,
        relationshipState: RelationshipState
    ): CompanionPolicy {
        if (userState.interactionAvailability == InteractionAvailability.DO_NOT_DISTURB ||
            userState.attentionState == AttentionState.FOCUSED
        ) {
            return CompanionPolicy(
                mode = CompanionMode.DO_NOT_DISTURB,
                initiativeLevel = 10,
                expressivenessLevel = 15,
                supportType = SupportType.NONE
            )
        }

        if (userState.mood == UserMood.SAD || userState.mood == UserMood.STRESSED) {
            return CompanionPolicy(
                mode = CompanionMode.GENTLE_CARE,
                initiativeLevel = 35,
                expressivenessLevel = 30,
                supportType = if (relationshipState.intimacyLevel > 45) SupportType.ENCOURAGEMENT else SupportType.LISTENING
            )
        }

        if (userState.mood == UserMood.TIRED) {
            return CompanionPolicy(
                mode = CompanionMode.PROACTIVE_HELP,
                initiativeLevel = 28,
                expressivenessLevel = 25,
                supportType = SupportType.REST_REMINDER
            )
        }

        if (petMindState.loneliness > 75 && relationshipState.acceptanceLevel > 45) {
            return CompanionPolicy(
                mode = CompanionMode.EMOTIONAL_SUPPORT,
                initiativeLevel = 42,
                expressivenessLevel = 35,
                supportType = SupportType.LISTENING
            )
        }

        if (userState.mood == UserMood.HAPPY || userState.mood == UserMood.CALM) {
            return CompanionPolicy(
                mode = CompanionMode.PLAYFUL_INTERACTION,
                initiativeLevel = (40 + petMindState.desireToInteract / 3).coerceIn(0, 100),
                expressivenessLevel = 70,
                supportType = SupportType.NONE
            )
        }

        return CompanionPolicy(
            mode = CompanionMode.QUIET_COMPANION,
            initiativeLevel = 20,
            expressivenessLevel = 20,
            supportType = SupportType.NONE
        )
    }
}

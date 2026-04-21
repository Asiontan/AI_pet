package com.pet.pet.behavior.relationship

import com.pet.core.data.preferences.PetPreferences
import com.pet.core.domain.model.RelationshipState

class RelationshipPhaseManager(
    private val preferences: PetPreferences
) {
    enum class Phase {
        ACQUAINTANCE,
        FAMILIAR,
        ATTACHED,
        TUNED
    }

    fun resolvePhase(bondLevel: Int, intimacyLevel: Int, familiarityDays: Int): Phase {
        val phase = when {
            familiarityDays >= 21 && intimacyLevel >= 72 && bondLevel >= 70 -> Phase.TUNED
            familiarityDays >= 10 && intimacyLevel >= 55 && bondLevel >= 52 -> Phase.ATTACHED
            familiarityDays >= 4 && intimacyLevel >= 35 -> Phase.FAMILIAR
            else -> Phase.ACQUAINTANCE
        }
        preferences.saveRelationshipPhase(phase.name)
        return phase
    }

    fun currentPhase(): Phase {
        val stored = preferences.getRelationshipPhase()
        return runCatching { Phase.valueOf(stored) }.getOrDefault(Phase.ACQUAINTANCE)
    }

    fun phaseExpressivenessBias(relationshipState: RelationshipState): Int = when (phaseOf(relationshipState)) {
        Phase.ACQUAINTANCE -> -8
        Phase.FAMILIAR -> 0
        Phase.ATTACHED -> 6
        Phase.TUNED -> 10
    }

    fun phaseInitiativeBias(relationshipState: RelationshipState): Int = when (phaseOf(relationshipState)) {
        Phase.ACQUAINTANCE -> -10
        Phase.FAMILIAR -> -2
        Phase.ATTACHED -> 4
        Phase.TUNED -> 8
    }

    private fun phaseOf(relationshipState: RelationshipState): Phase {
        return runCatching { Phase.valueOf(relationshipState.phase) }.getOrDefault(currentPhase())
    }
}

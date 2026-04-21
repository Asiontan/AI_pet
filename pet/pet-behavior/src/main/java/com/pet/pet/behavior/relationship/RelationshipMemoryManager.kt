package com.pet.pet.behavior.relationship

import com.pet.core.data.preferences.PetPreferences
import com.pet.core.domain.model.RelationshipState

class RelationshipMemoryManager(
    private val preferences: PetPreferences,
    private val relationshipPhaseManager: RelationshipPhaseManager
) {
    fun refreshDaily() {
        preferences.incrementFamiliarityDayIfNeeded()
    }

    fun recordPositiveInteraction(delta: Int = 1) {
        preferences.addBond(delta)
        preferences.addRecentInteractionScore(4 * delta)
        preferences.addAcceptanceLevel(2 * delta)
        preferences.addNeglectLevel(-3 * delta)
    }

    fun recordPassivePeriod() {
        preferences.addNeglectLevel(2)
        preferences.addRecentInteractionScore(-1)
    }

    fun recordIgnoredSuggestion() {
        preferences.addAcceptanceLevel(-2)
        preferences.addNeglectLevel(1)
    }

    fun buildState(): RelationshipState {
        val bondLevel = preferences.getBondLevel().coerceIn(0, 100)
        val interactionScore = preferences.getRecentInteractionScore().coerceIn(0, 100)
        val acceptance = preferences.getAcceptanceLevel().coerceIn(0, 100)
        val neglect = preferences.getNeglectLevel().coerceIn(0, 100)
        val familiarity = preferences.getFamiliarityDays().coerceAtLeast(0)
        val intimacy = ((bondLevel * 0.6f) + (interactionScore * 0.25f) + (acceptance * 0.15f)).toInt().coerceIn(0, 100)
        val phase = relationshipPhaseManager.resolvePhase(bondLevel, intimacy, familiarity)

        return RelationshipState(
            bondLevel = bondLevel,
            intimacyLevel = intimacy,
            neglectLevel = neglect,
            acceptanceLevel = acceptance,
            familiarityDays = familiarity,
            phase = phase.name
        )
    }
}

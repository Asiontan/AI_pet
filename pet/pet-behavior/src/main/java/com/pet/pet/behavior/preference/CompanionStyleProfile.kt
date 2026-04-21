package com.pet.pet.behavior.preference

import com.pet.core.data.preferences.PetPreferences
import com.pet.core.domain.model.CompanionMode
import com.pet.core.domain.model.CompanionPolicy
import com.pet.core.domain.model.RelationshipState
import com.pet.pet.behavior.relationship.RelationshipPhaseManager

class CompanionStyleProfile(
    private val preferences: PetPreferences,
    private val relationshipPhaseManager: RelationshipPhaseManager
) {
    enum class StyleMode {
        GENTLE,
        PLAYFUL,
        RESERVED,
        NIGHT_CALM
    }

    fun currentStyle(): StyleMode {
        val stored = preferences.getCompanionStyleMode()
        return runCatching { StyleMode.valueOf(stored) }.getOrDefault(deriveStyle())
    }

    fun apply(policy: CompanionPolicy, relationshipState: RelationshipState): CompanionPolicy {
        val phaseInitiativeBias = relationshipPhaseManager.phaseInitiativeBias(relationshipState)
        val phaseExpressivenessBias = relationshipPhaseManager.phaseExpressivenessBias(relationshipState)
        val phaseAdjusted = policy.copy(
            initiativeLevel = (policy.initiativeLevel + phaseInitiativeBias).coerceIn(0, 100),
            expressivenessLevel = (policy.expressivenessLevel + phaseExpressivenessBias).coerceIn(0, 100)
        )

        return when (currentStyle()) {
            StyleMode.GENTLE -> phaseAdjusted.copy(
                initiativeLevel = (phaseAdjusted.initiativeLevel - 4).coerceIn(0, 100),
                expressivenessLevel = (phaseAdjusted.expressivenessLevel - 6).coerceIn(0, 100),
                mode = if (phaseAdjusted.mode == CompanionMode.PLAYFUL_INTERACTION) CompanionMode.GENTLE_CARE else phaseAdjusted.mode
            )
            StyleMode.PLAYFUL -> phaseAdjusted.copy(
                initiativeLevel = (phaseAdjusted.initiativeLevel + 6).coerceIn(0, 100),
                expressivenessLevel = (phaseAdjusted.expressivenessLevel + 8).coerceIn(0, 100)
            )
            StyleMode.RESERVED -> phaseAdjusted.copy(
                initiativeLevel = (phaseAdjusted.initiativeLevel - 10).coerceIn(0, 100),
                expressivenessLevel = (phaseAdjusted.expressivenessLevel - 10).coerceIn(0, 100),
                mode = if (phaseAdjusted.mode == CompanionMode.PROACTIVE_HELP) CompanionMode.QUIET_COMPANION else phaseAdjusted.mode
            )
            StyleMode.NIGHT_CALM -> phaseAdjusted.copy(
                initiativeLevel = (phaseAdjusted.initiativeLevel - 6).coerceIn(0, 100),
                expressivenessLevel = (phaseAdjusted.expressivenessLevel - 4).coerceIn(0, 100)
            )
        }
    }

    fun learnFromPositiveMode(mode: String) {
        when (mode) {
            CompanionMode.GENTLE_CARE.name, CompanionMode.EMOTIONAL_SUPPORT.name -> preferences.addStyleWeight(StyleMode.GENTLE.name, 2)
            CompanionMode.PLAYFUL_INTERACTION.name -> preferences.addStyleWeight(StyleMode.PLAYFUL.name, 2)
            CompanionMode.QUIET_COMPANION.name -> preferences.addStyleWeight(StyleMode.RESERVED.name, 2)
            CompanionMode.PROACTIVE_HELP.name -> preferences.addStyleWeight(StyleMode.NIGHT_CALM.name, 1)
        }
        syncStoredStyle()
    }

    fun learnFromIgnoredMode(mode: String) {
        when (mode) {
            CompanionMode.PLAYFUL_INTERACTION.name -> preferences.addStyleWeight(StyleMode.PLAYFUL.name, -2)
            CompanionMode.PROACTIVE_HELP.name -> preferences.addStyleWeight(StyleMode.NIGHT_CALM.name, -1)
            else -> preferences.addStyleWeight(StyleMode.RESERVED.name, 1)
        }
        syncStoredStyle()
    }

    private fun deriveStyle(): StyleMode {
        val weights = StyleMode.entries.associateWith { preferences.getStyleWeight(it.name) }
        return weights.maxByOrNull { it.value }?.key ?: StyleMode.GENTLE
    }

    private fun syncStoredStyle() {
        preferences.saveCompanionStyleMode(deriveStyle().name)
    }
}

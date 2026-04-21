package com.pet.pet.behavior.preference

import com.pet.core.data.preferences.PetPreferences
import com.pet.core.domain.model.CompanionMode
import com.pet.core.domain.model.CompanionPolicy
import com.pet.core.domain.model.PetMindState
import com.pet.core.domain.model.RelationshipState
import com.pet.core.domain.model.UserState
import com.pet.pet.behavior.policy.ContextualPolicySelector

class PreferenceLearningCenter(
    private val preferences: PetPreferences,
    private val contextualPolicySelector: ContextualPolicySelector,
    private val adaptivePolicyScorer: AdaptivePolicyScorer,
    private val companionStyleProfile: CompanionStyleProfile
) {
    fun refinePolicy(
        basePolicy: CompanionPolicy,
        userState: UserState,
        petMindState: PetMindState,
        relationshipState: RelationshipState
    ): CompanionPolicy {
        val contextualPolicy = contextualPolicySelector.select(basePolicy, userState, petMindState, relationshipState)
        val scoredPolicy = adaptivePolicyScorer.score(contextualPolicy, userState)
        val styledPolicy = companionStyleProfile.apply(scoredPolicy, relationshipState)
        val affinity = preferences.getModeAffinity(styledPolicy.mode.name)

        val adaptedMode = when {
            styledPolicy.mode == CompanionMode.PROACTIVE_HELP && affinity < 35 -> CompanionMode.QUIET_COMPANION
            styledPolicy.mode == CompanionMode.PLAYFUL_INTERACTION && affinity < 30 -> CompanionMode.QUIET_COMPANION
            else -> styledPolicy.mode
        }

        preferences.saveLastCompanionMode(adaptedMode.name)
        return styledPolicy.copy(mode = adaptedMode)
    }

    fun recordPositiveResponse() {
        val mode = preferences.getLastCompanionMode()
        preferences.addModeAffinity(mode, 4)
        preferences.updatePreferredInteractionHour(currentHour())
        companionStyleProfile.learnFromPositiveMode(mode)
    }

    fun recordChatCompletion() {
        val mode = preferences.getLastCompanionMode()
        preferences.addModeAffinity(mode, 6)
        preferences.updatePreferredInteractionHour(currentHour())
        companionStyleProfile.learnFromPositiveMode(mode)
    }

    fun recordIgnoredCare() {
        val mode = preferences.getLastCompanionMode()
        preferences.addModeAffinity(mode, -5)
        companionStyleProfile.learnFromIgnoredMode(mode)
    }

    fun recordPassiveDecay() {
        val mode = preferences.getLastCompanionMode()
        if (mode == CompanionMode.PLAYFUL_INTERACTION.name || mode == CompanionMode.PROACTIVE_HELP.name) {
            preferences.addModeAffinity(mode, -2)
            companionStyleProfile.learnFromIgnoredMode(mode)
        }
    }

    private fun currentHour(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
}

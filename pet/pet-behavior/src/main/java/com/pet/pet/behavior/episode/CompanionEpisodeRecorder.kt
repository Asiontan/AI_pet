package com.pet.pet.behavior.episode

import com.pet.core.data.preferences.PetPreferences
import com.pet.core.domain.model.CompanionPolicy
import com.pet.core.domain.model.RelationshipState
import com.pet.core.domain.model.UserState

class CompanionEpisodeRecorder(
    private val preferences: PetPreferences
) {
    fun startEpisode(
        userState: UserState,
        relationshipState: RelationshipState,
        policy: CompanionPolicy,
        timestamp: Long = System.currentTimeMillis()
    ) {
        preferences.saveEpisodeMode(policy.mode.name)
        preferences.saveEpisodeUserMood(userState.mood.name)
        preferences.saveEpisodeStartTime(timestamp)
        preferences.saveEpisodeIntimacy(relationshipState.intimacyLevel)
        preferences.saveEpisodeActive(true)
    }

    fun completePositive(timestamp: Long = System.currentTimeMillis()) {
        finishEpisode("positive", 1, timestamp)
    }

    fun completeChat(timestamp: Long = System.currentTimeMillis()) {
        finishEpisode("chat", 2, timestamp)
    }

    fun completeIgnored(timestamp: Long = System.currentTimeMillis()) {
        finishEpisode("ignored", -1, timestamp)
    }

    fun completePassive(timestamp: Long = System.currentTimeMillis()) {
        finishEpisode("passive", -1, timestamp)
    }

    private fun finishEpisode(outcome: String, rewardDelta: Int, timestamp: Long) {
        if (!preferences.isEpisodeActive()) return
        val mode = preferences.getEpisodeMode()
        val startedAt = preferences.getEpisodeStartTime()
        val durationMin = if (startedAt > 0L) ((timestamp - startedAt) / 60_000L).toInt().coerceAtLeast(0) else 0
        preferences.saveEpisodeOutcome(outcome)
        preferences.saveEpisodeDurationMinutes(durationMin)
        preferences.saveEpisodeActive(false)
        preferences.incrementEpisodeCount()
        if (rewardDelta > 0) preferences.incrementPositiveEpisodeCount() else preferences.incrementNegativeEpisodeCount()
        if (mode.isNotBlank()) preferences.addEpisodeReward(mode, rewardDelta)
    }
}

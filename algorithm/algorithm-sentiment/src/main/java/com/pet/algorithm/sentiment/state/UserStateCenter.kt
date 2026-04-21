package com.pet.algorithm.sentiment.state

import com.pet.algorithm.sentiment.EmotionLabel
import com.pet.core.domain.model.AttentionState
import com.pet.core.domain.model.InteractionAvailability
import com.pet.core.domain.model.LifeContext
import com.pet.core.domain.model.UserMood
import com.pet.core.domain.model.UserState
import java.util.Calendar

class UserStateCenter {
    fun resolve(
        behaviorEmotion: Int,
        textEmotion: EmotionLabel?,
        lastInteractionDeltaMs: Long,
        foregroundApp: String?
    ): UserState {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val lifeContext = resolveLifeContext(hour, foregroundApp)
        val mood = resolveMood(behaviorEmotion, textEmotion, lifeContext)
        val stressLevel = ((10 - behaviorEmotion).coerceIn(0, 10) * 10)
        val energyLevel = resolveEnergy(behaviorEmotion, hour)
        val attentionState = resolveAttention(lastInteractionDeltaMs, foregroundApp)
        val availability = resolveAvailability(mood, attentionState, lifeContext)

        return UserState(
            mood = mood,
            energyLevel = energyLevel,
            stressLevel = stressLevel,
            attentionState = attentionState,
            interactionAvailability = availability,
            lifeContext = lifeContext,
            confidence = if (textEmotion == null) 0.55f else 0.75f
        )
    }

    private fun resolveLifeContext(hour: Int, foregroundApp: String?): LifeContext = when {
        hour in 6..10 -> LifeContext.MORNING
        hour >= 23 || hour < 5 -> LifeContext.LATE_NIGHT
        foregroundApp?.contains("code", ignoreCase = true) == true ||
            foregroundApp?.contains("studio", ignoreCase = true) == true ||
            foregroundApp?.contains("docs", ignoreCase = true) == true -> LifeContext.WORKING
        foregroundApp?.contains("bili", ignoreCase = true) == true ||
            foregroundApp?.contains("douyin", ignoreCase = true) == true -> LifeContext.RESTING
        else -> LifeContext.UNKNOWN
    }

    private fun resolveMood(
        behaviorEmotion: Int,
        textEmotion: EmotionLabel?,
        lifeContext: LifeContext
    ): UserMood {
        textEmotion?.let {
            return when (it) {
                EmotionLabel.POSITIVE -> UserMood.HAPPY
                EmotionLabel.NEGATIVE -> if (lifeContext == LifeContext.LATE_NIGHT) UserMood.TIRED else UserMood.SAD
                EmotionLabel.NEUTRAL -> resolveMoodFromBehavior(behaviorEmotion, lifeContext)
            }
        }
        return resolveMoodFromBehavior(behaviorEmotion, lifeContext)
    }

    private fun resolveMoodFromBehavior(behaviorEmotion: Int, lifeContext: LifeContext): UserMood = when {
        lifeContext == LifeContext.LATE_NIGHT && behaviorEmotion <= 5 -> UserMood.TIRED
        behaviorEmotion >= 8 -> UserMood.HAPPY
        behaviorEmotion >= 6 -> UserMood.CALM
        behaviorEmotion <= 2 -> UserMood.STRESSED
        behaviorEmotion <= 4 -> UserMood.SAD
        else -> UserMood.UNKNOWN
    }

    private fun resolveEnergy(behaviorEmotion: Int, hour: Int): Int {
        val base = (behaviorEmotion * 10).coerceIn(20, 95)
        return if (hour >= 23 || hour < 6) (base - 20).coerceAtLeast(10) else base
    }

    private fun resolveAttention(lastInteractionDeltaMs: Long, foregroundApp: String?): AttentionState = when {
        foregroundApp?.contains("code", ignoreCase = true) == true ||
            foregroundApp?.contains("studio", ignoreCase = true) == true -> AttentionState.FOCUSED
        lastInteractionDeltaMs < 30_000L -> AttentionState.ACTIVE
        lastInteractionDeltaMs < 5 * 60_000L -> AttentionState.DISTRACTED
        else -> AttentionState.IDLE
    }

    private fun resolveAvailability(
        mood: UserMood,
        attentionState: AttentionState,
        lifeContext: LifeContext
    ): InteractionAvailability = when {
        attentionState == AttentionState.FOCUSED -> InteractionAvailability.DO_NOT_DISTURB
        lifeContext == LifeContext.LATE_NIGHT && mood == UserMood.TIRED -> InteractionAvailability.LOW
        mood == UserMood.STRESSED -> InteractionAvailability.LOW
        else -> InteractionAvailability.OPEN
    }
}

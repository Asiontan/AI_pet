package com.pet.core.domain.model

enum class UserMood {
    HAPPY,
    CALM,
    TIRED,
    STRESSED,
    LONELY,
    SAD,
    IRRITATED,
    FOCUSED,
    UNKNOWN
}

enum class AttentionState {
    FOCUSED,
    DISTRACTED,
    IDLE,
    ACTIVE
}

enum class InteractionAvailability {
    OPEN,
    LOW,
    DO_NOT_DISTURB
}

enum class LifeContext {
    MORNING,
    WORKING,
    STUDYING,
    COMMUTING,
    RESTING,
    LATE_NIGHT,
    UNKNOWN
}

data class UserState(
    val mood: UserMood,
    val energyLevel: Int,
    val stressLevel: Int,
    val attentionState: AttentionState,
    val interactionAvailability: InteractionAvailability,
    val lifeContext: LifeContext,
    val confidence: Float
)

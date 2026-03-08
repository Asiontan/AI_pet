package com.pet.core.domain.model

data class PetState(
    val id: String = "default",
    val position: PetPosition = PetPosition(),
    val behaviorState: BehaviorState = BehaviorState.IDLE,
    val animationState: AnimationState = AnimationState.IDLE,
    val lastUpdateTimestamp: Long = System.currentTimeMillis()
)

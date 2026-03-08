package com.pet.core.domain.model

enum class BehaviorState {
    IDLE, WALK, DRAG, CLICK, LONG_PRESS, SLEEP, WAKE_UP;

    fun getAnimationState(): AnimationState {
        return when (this) {
            IDLE -> AnimationState.IDLE
            WALK -> AnimationState.WALK
            DRAG -> AnimationState.DRAG
            CLICK -> AnimationState.CLICK
            LONG_PRESS -> AnimationState.LONG_PRESS
            SLEEP -> AnimationState.SLEEP
            WAKE_UP -> AnimationState.WAKE_UP
        }
    }
}

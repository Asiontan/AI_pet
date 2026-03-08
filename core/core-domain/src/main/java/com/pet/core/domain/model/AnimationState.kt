package com.pet.core.domain.model

enum class AnimationState {
    IDLE, WALK, CLICK, DRAG, SLEEP, WAKE_UP, LONG_PRESS, CUSTOM;

    fun isLooping(): Boolean {
        return this in setOf(IDLE, WALK, SLEEP)
    }
}

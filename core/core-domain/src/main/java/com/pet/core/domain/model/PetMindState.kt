package com.pet.core.domain.model

enum class PetMood {
    HAPPY,
    CALM,
    EXCITED,
    SLEEPY,
    SAD,
    WORRIED,
    LONELY,
    SHY
}

data class PetMindState(
    val mood: PetMood,
    val energy: Int,
    val attachment: Int,
    val loneliness: Int,
    val trust: Int,
    val desireToInteract: Int,
    val careLevel: Int
)

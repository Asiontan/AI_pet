package com.pet.core.domain.model

data class PetPosition(
    val x: Int = 0,
    val y: Int = 0,
    val isEdgeSnapped: Boolean = false
)

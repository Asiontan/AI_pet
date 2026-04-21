package com.pet.core.domain.model

data class RelationshipState(
    val bondLevel: Int,
    val intimacyLevel: Int,
    val neglectLevel: Int,
    val acceptanceLevel: Int,
    val familiarityDays: Int,
    val phase: String = "ACQUAINTANCE"
)

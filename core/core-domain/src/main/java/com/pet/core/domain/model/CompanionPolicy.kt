package com.pet.core.domain.model

enum class CompanionMode {
    QUIET_COMPANION,
    GENTLE_CARE,
    PLAYFUL_INTERACTION,
    PROACTIVE_HELP,
    EMOTIONAL_SUPPORT,
    DO_NOT_DISTURB
}

enum class SupportType {
    NONE,
    LISTENING,
    ENCOURAGEMENT,
    REST_REMINDER,
    WATER_REMINDER,
    SLEEP_REMINDER,
    TASK_SUPPORT
}

data class CompanionPolicy(
    val mode: CompanionMode,
    val initiativeLevel: Int,
    val expressivenessLevel: Int,
    val supportType: SupportType
)

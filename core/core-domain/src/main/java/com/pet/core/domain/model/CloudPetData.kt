package com.pet.core.domain.model

/**
 * 需要同步到云端的宠物数据快照。
 * 包含宠物的核心状态、情绪值、亲密度等需要跨设备保持一致的数据。
 */
data class CloudPetData(
    val objectId: String = "",
    val userId: String = "",
    val petX: Int = 0,
    val petY: Int = 0,
    val emotion: Int = 5,
    val bondLevel: Int = 0,
    val totalOnlineMinutes: Long = 0,
    val totalChatCount: Int = 0,
    val familiarityDays: Int = 0,
    val petTrust: Int = 50,
    val petEnergy: Int = 70,
    val petMood: String = "CALM",
    val updatedAt: String = ""
)

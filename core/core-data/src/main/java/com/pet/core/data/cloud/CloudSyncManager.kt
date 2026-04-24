package com.pet.core.data.cloud

import com.pet.core.common.logger.PetLogger
import com.pet.core.common.result.Result
import com.pet.core.data.preferences.PetPreferences
import com.pet.core.data.repository.BmobRepository
import com.pet.core.domain.model.CloudPetData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 云端同步管理器。
 *
 * 协调本地 [PetPreferences] 与云端 [BmobRepository] 之间的数据同步。
 * - [syncToCloud]：将本地数据打包上传至 Bmob
 * - [syncFromCloud]：从 Bmob 拉取数据并覆盖本地
 */
class CloudSyncManager(
    private val bmobRepository: BmobRepository,
    private val preferences: PetPreferences
) {

    companion object {
        private const val TAG = "CloudSyncManager"
    }

    /**
     * 将当前本地的宠物状态数据同步到云端。
     * 需要用户已登录。
     */
    suspend fun syncToCloud(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!bmobRepository.isLoggedIn()) {
            return@withContext Result.Error(Exception("未登录"))
        }

        try {
            val data = CloudPetData(
                petX = preferences.getPetX(),
                petY = preferences.getPetY(),
                emotion = preferences.getPetEmotion(),
                bondLevel = preferences.getBondLevel(),
                totalOnlineMinutes = preferences.getTotalOnlineMinutes(),
                totalChatCount = preferences.getTotalChatCount(),
                familiarityDays = preferences.getFamiliarityDays(),
                petTrust = preferences.getPetTrust(),
                petEnergy = preferences.getPetEnergy(),
                petMood = preferences.getPetMood()
            )

            when (val result = bmobRepository.uploadPetData(data)) {
                is Result.Success -> {
                    PetLogger.i(TAG, "同步到云端成功")
                    Result.Success(Unit)
                }
                is Result.Error -> {
                    PetLogger.e(TAG, "同步到云端失败", result.exception)
                    result
                }
                is Result.Loading -> Result.Loading
            }
        } catch (e: Exception) {
            PetLogger.e(TAG, "同步到云端异常", e)
            Result.Error(e)
        }
    }

    /**
     * 从云端拉取宠物数据并覆盖本地状态。
     * 需要用户已登录。
     */
    suspend fun syncFromCloud(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!bmobRepository.isLoggedIn()) {
            return@withContext Result.Error(Exception("未登录"))
        }

        val userId = preferences.getBmobUserId()
        if (userId.isEmpty()) {
            return@withContext Result.Error(Exception("用户 ID 为空"))
        }

        try {
            when (val result = bmobRepository.downloadPetData(userId)) {
                is Result.Success -> {
                    val data = result.data
                    if (data == null) {
                        PetLogger.i(TAG, "云端暂无数据")
                        return@withContext Result.Success(Unit)
                    }
                    applyCloudData(data)
                    PetLogger.i(TAG, "从云端同步成功")
                    Result.Success(Unit)
                }
                is Result.Error -> {
                    PetLogger.e(TAG, "从云端同步失败", result.exception)
                    result
                }
                is Result.Loading -> Result.Loading
            }
        } catch (e: Exception) {
            PetLogger.e(TAG, "从云端同步异常", e)
            Result.Error(e)
        }
    }

    private suspend fun applyCloudData(data: CloudPetData) {
        preferences.savePetX(data.petX)
        preferences.savePetY(data.petY)
        preferences.savePetEmotion(data.emotion)
        preferences.saveBondLevel(data.bondLevel)
        preferences.saveFamiliarityDays(data.familiarityDays)
        preferences.savePetTrust(data.petTrust)
        preferences.savePetEnergy(data.petEnergy)
        preferences.savePetMood(data.petMood)
    }
}

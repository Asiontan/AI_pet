package com.pet.core.data.repository

import com.pet.core.domain.model.PetState
import com.pet.core.domain.model.PetPosition
import com.pet.core.domain.repository.IPetRepository
import com.pet.core.data.preferences.PetPreferences
import com.pet.core.common.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PetRepository(
    private val preferences: PetPreferences
) : IPetRepository {

    override suspend fun getPetState(): Result<PetState> = withContext(Dispatchers.IO) {
        try {
            val state = PetState(
                position = PetPosition(
                    x = preferences.getPetX(),
                    y = preferences.getPetY()
                )
            )
            Result.Success(state)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun savePetState(state: PetState): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            preferences.savePetX(state.position.x)
            preferences.savePetY(state.position.y)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun getPetPosition(): Result<PetPosition> = withContext(Dispatchers.IO) {
        try {
            val position = PetPosition(
                x = preferences.getPetX(),
                y = preferences.getPetY()
            )
            Result.Success(position)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun savePetPosition(position: PetPosition): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            preferences.savePetX(position.x)
            preferences.savePetY(position.y)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ── 情绪值持久化 ──────────────────────────────────────────────────
    /** 保存宠物情绪值 */
    suspend fun savePetEmotion(emotion: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            preferences.savePetEmotion(emotion)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /** 读取宠物情绪值 */
    suspend fun getPetEmotion(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Result.Success(preferences.getPetEmotion())
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ── 交互统计持久化 ────────────────────────────────────────────────
    /** 记录一次点击，累加今日计数 */
    suspend fun recordClick(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val count = preferences.getTodayClickCount() + 1
            preferences.saveTodayClickCount(count)
            preferences.saveLastInteractionTime(System.currentTimeMillis())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /** 读取今日点击次数 */
    suspend fun getTodayClickCount(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Result.Success(preferences.getTodayClickCount())
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}

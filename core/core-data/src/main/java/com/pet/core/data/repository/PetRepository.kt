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
}

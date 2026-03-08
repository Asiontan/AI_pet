package com.pet.core.domain.repository

import com.pet.core.domain.model.PetState
import com.pet.core.domain.model.PetPosition
import com.pet.core.common.result.Result

interface IPetRepository {
    suspend fun getPetState(): Result<PetState>
    suspend fun savePetState(state: PetState): Result<Unit>
    suspend fun getPetPosition(): Result<PetPosition>
    suspend fun savePetPosition(position: PetPosition): Result<Unit>
}

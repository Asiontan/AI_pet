package com.pet.pet.behavior.mind

import com.pet.core.data.preferences.PetPreferences
import com.pet.core.domain.model.PetMindState
import com.pet.core.domain.model.PetMood
import com.pet.core.domain.model.RelationshipState
import com.pet.core.domain.model.UserMood
import com.pet.core.domain.model.UserState
import kotlin.math.roundToInt

class PetMindEngine(
    private val preferences: PetPreferences
) {
    fun buildMindState(userState: UserState, relationshipState: RelationshipState): PetMindState {
        val storedEnergy = preferences.getPetEnergy()
        val storedLoneliness = preferences.getPetLoneliness()
        val storedTrust = preferences.getPetTrust()

        val energy = adjustEnergy(storedEnergy, userState)
        val loneliness = adjustLoneliness(storedLoneliness, relationshipState)
        val trust = ((storedTrust + relationshipState.acceptanceLevel) / 2f).roundToInt().coerceIn(0, 100)
        val attachment = ((relationshipState.bondLevel * 0.7f) + (relationshipState.intimacyLevel * 0.3f))
            .roundToInt()
            .coerceIn(0, 100)
        val careLevel = when (userState.mood) {
            UserMood.SAD, UserMood.STRESSED, UserMood.TIRED, UserMood.LONELY -> (65 + attachment / 4).coerceIn(0, 100)
            else -> (35 + attachment / 5).coerceIn(0, 100)
        }
        val desireToInteract = when (userState.mood) {
            UserMood.FOCUSED -> 15
            UserMood.TIRED, UserMood.STRESSED, UserMood.SAD -> 25
            UserMood.HAPPY, UserMood.CALM -> 70
            else -> 45
        }.let { base -> (base + attachment / 5 - loneliness / 6).coerceIn(0, 100) }

        val mood = resolveMood(userState, energy, loneliness, careLevel)

        preferences.savePetEnergy(energy)
        preferences.savePetLoneliness(loneliness)
        preferences.savePetTrust(trust)
        preferences.savePetMood(mood.name)

        return PetMindState(
            mood = mood,
            energy = energy,
            attachment = attachment,
            loneliness = loneliness,
            trust = trust,
            desireToInteract = desireToInteract,
            careLevel = careLevel
        )
    }

    private fun adjustEnergy(current: Int, userState: UserState): Int {
        return (current + when (userState.mood) {
            UserMood.TIRED -> -8
            UserMood.STRESSED, UserMood.SAD -> -4
            UserMood.HAPPY -> 6
            UserMood.CALM -> 2
            else -> 0
        }).coerceIn(0, 100)
    }

    private fun adjustLoneliness(current: Int, relationshipState: RelationshipState): Int {
        return (current + relationshipState.neglectLevel / 8 - relationshipState.bondLevel / 20).coerceIn(0, 100)
    }

    private fun resolveMood(
        userState: UserState,
        energy: Int,
        loneliness: Int,
        careLevel: Int
    ): PetMood {
        return when {
            energy < 25 -> PetMood.SLEEPY
            userState.mood == UserMood.SAD || userState.mood == UserMood.STRESSED -> PetMood.WORRIED
            loneliness > 70 -> PetMood.LONELY
            userState.mood == UserMood.HAPPY && careLevel < 60 -> PetMood.HAPPY
            energy > 80 && userState.mood == UserMood.CALM -> PetMood.EXCITED
            else -> PetMood.CALM
        }
    }
}

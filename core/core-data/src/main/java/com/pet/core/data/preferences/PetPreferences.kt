package com.pet.core.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.pet.core.common.constant.PetConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PetPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PetConstants.PREF_NAME,
        Context.MODE_PRIVATE
    )

    suspend fun savePetX(x: Int) = withContext(Dispatchers.IO) {
        prefs.edit().putInt(PetConstants.PREF_KEY_PET_X, x).apply()
    }

    suspend fun getPetX(): Int = withContext(Dispatchers.IO) {
        prefs.getInt(PetConstants.PREF_KEY_PET_X, 0)
    }

    suspend fun savePetY(y: Int) = withContext(Dispatchers.IO) {
        prefs.edit().putInt(PetConstants.PREF_KEY_PET_Y, y).apply()
    }

    suspend fun getPetY(): Int = withContext(Dispatchers.IO) {
        prefs.getInt(PetConstants.PREF_KEY_PET_Y, 0)
    }

    suspend fun saveEdgeSnapped(isSnapped: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("edge_snapped", isSnapped).apply()
    }

    suspend fun getEdgeSnapped(): Boolean = withContext(Dispatchers.IO) {
        prefs.getBoolean("edge_snapped", false)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }
}

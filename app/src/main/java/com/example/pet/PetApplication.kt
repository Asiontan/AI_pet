package com.example.pet

import android.app.Application
import com.pet.core.common.logger.PetLogger

class PetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PetLogger.setDebugMode(true)
        PetLogger.d("PetApplication", "Pet Desktop started")
    }
}

package com.example.pet

import android.app.Application
import com.pet.core.common.logger.PetLogger
import com.pet.core.data.cloud.CloudSyncManager
import com.pet.core.data.network.BmobApiClient
import com.pet.core.data.preferences.PetPreferences
import com.pet.core.data.repository.BmobRepository

class PetApplication : Application() {

    lateinit var petPreferences: PetPreferences
        private set
    lateinit var bmobRepository: BmobRepository
        private set
    lateinit var cloudSyncManager: CloudSyncManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        PetLogger.setDebugMode(true)
        PetLogger.d(TAG, "Pet Desktop started")

        petPreferences = PetPreferences(this)

        val bmobApiClient = BmobApiClient(
            appId = BuildConfig.BMOB_APP_ID,
            restApiKey = BuildConfig.BMOB_REST_API_KEY
        )
        bmobRepository = BmobRepository(bmobApiClient, petPreferences)
        cloudSyncManager = CloudSyncManager(bmobRepository, petPreferences)

        if (bmobRepository.isLoggedIn()) {
            PetLogger.i(TAG, "Bmob 用户已登录: ${petPreferences.getBmobUsername()}")
        }
    }

    companion object {
        private const val TAG = "PetApplication"

        lateinit var instance: PetApplication
            private set
    }
}

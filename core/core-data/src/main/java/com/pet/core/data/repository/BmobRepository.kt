package com.pet.core.data.repository

import com.pet.core.common.logger.PetLogger
import com.pet.core.common.result.Result
import com.pet.core.data.network.ApiResult
import com.pet.core.data.network.BmobApiClient
import com.pet.core.data.preferences.PetPreferences
import com.pet.core.domain.model.BmobUser
import com.pet.core.domain.model.CloudPetData
import com.pet.core.domain.repository.IBmobRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Bmob 云端服务的 Repository 实现。
 *
 * 负责用户账号管理（注册/登录/注销）和宠物数据的云端同步。
 * 使用 Bmob REST API，通过 [BmobApiClient] 发起网络请求。
 *
 * 云端数据表：PetData —— 存储用户的宠物状态快照
 */
class BmobRepository(
    private val apiClient: BmobApiClient,
    private val preferences: PetPreferences
) : IBmobRepository {

    companion object {
        private const val TAG = "BmobRepository"
        private const val TABLE_PET_DATA = "PetData"
    }

    init {
        val savedToken = preferences.getBmobSessionToken()
        if (savedToken.isNotEmpty()) {
            apiClient.sessionToken = savedToken
        }
    }

    // ── 用户账号 ─────────────────────────────────────────────────────

    override suspend fun register(
        username: String,
        password: String
    ): Result<BmobUser> = withContext(Dispatchers.IO) {
        when (val result = apiClient.registerUser(username, password)) {
            is ApiResult.Success -> {
                val json = result.data
                val user = BmobUser(
                    objectId = json.optString("objectId"),
                    username = username,
                    sessionToken = json.optString("sessionToken"),
                    createdAt = json.optString("createdAt")
                )
                saveSession(user)
                PetLogger.i(TAG, "注册成功: ${user.objectId}")
                Result.Success(user)
            }
            is ApiResult.HttpError -> {
                PetLogger.w(TAG, "注册失败: ${result.code} ${result.message}")
                Result.Error(Exception("注册失败(${result.code}): ${result.message}"))
            }
            is ApiResult.NetworkError -> {
                PetLogger.e(TAG, "注册网络错误", result.cause)
                Result.Error(Exception("网络连接失败", result.cause))
            }
            is ApiResult.ParseError -> {
                PetLogger.e(TAG, "注册解析错误", result.cause)
                Result.Error(Exception("数据解析失败", result.cause))
            }
        }
    }

    override suspend fun login(
        username: String,
        password: String
    ): Result<BmobUser> = withContext(Dispatchers.IO) {
        when (val result = apiClient.loginUser(username, password)) {
            is ApiResult.Success -> {
                val json = result.data
                val user = BmobUser(
                    objectId = json.optString("objectId"),
                    username = json.optString("username"),
                    sessionToken = json.optString("sessionToken"),
                    createdAt = json.optString("createdAt"),
                    updatedAt = json.optString("updatedAt")
                )
                saveSession(user)
                PetLogger.i(TAG, "登录成功: ${user.username}")
                Result.Success(user)
            }
            is ApiResult.HttpError -> {
                PetLogger.w(TAG, "登录失败: ${result.code} ${result.message}")
                Result.Error(Exception("登录失败: 用户名或密码错误"))
            }
            is ApiResult.NetworkError -> {
                PetLogger.e(TAG, "登录网络错误", result.cause)
                Result.Error(Exception("网络连接失败", result.cause))
            }
            is ApiResult.ParseError -> {
                PetLogger.e(TAG, "登录解析错误", result.cause)
                Result.Error(Exception("数据解析失败", result.cause))
            }
        }
    }

    override suspend fun getCurrentUser(): Result<BmobUser?> = withContext(Dispatchers.IO) {
        val userId = preferences.getBmobUserId()
        if (userId.isEmpty()) return@withContext Result.Success(null)

        when (val result = apiClient.getCurrentUser(userId)) {
            is ApiResult.Success -> {
                val json = result.data
                Result.Success(
                    BmobUser(
                        objectId = json.optString("objectId"),
                        username = json.optString("username"),
                        sessionToken = preferences.getBmobSessionToken(),
                        createdAt = json.optString("createdAt"),
                        updatedAt = json.optString("updatedAt")
                    )
                )
            }
            is ApiResult.HttpError -> {
                if (result.code == 401) {
                    preferences.clearBmobSession()
                    apiClient.sessionToken = ""
                }
                Result.Error(Exception("获取用户信息失败(${result.code})"))
            }
            is ApiResult.NetworkError -> Result.Error(Exception("网络连接失败", result.cause))
            is ApiResult.ParseError -> Result.Error(Exception("数据解析失败", result.cause))
        }
    }

    override suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        preferences.clearBmobSession()
        apiClient.sessionToken = ""
        PetLogger.i(TAG, "已注销登录")
        Result.Success(Unit)
    }

    override fun isLoggedIn(): Boolean = preferences.isBmobLoggedIn()

    // ── 数据同步 ─────────────────────────────────────────────────────

    override suspend fun uploadPetData(data: CloudPetData): Result<String> =
        withContext(Dispatchers.IO) {
            val userId = preferences.getBmobUserId()
            if (userId.isEmpty()) {
                return@withContext Result.Error(Exception("未登录，无法上传数据"))
            }

            val json = JSONObject().apply {
                put("userId", userId)
                put("petX", data.petX)
                put("petY", data.petY)
                put("emotion", data.emotion)
                put("bondLevel", data.bondLevel)
                put("totalOnlineMinutes", data.totalOnlineMinutes)
                put("totalChatCount", data.totalChatCount)
                put("familiarityDays", data.familiarityDays)
                put("petTrust", data.petTrust)
                put("petEnergy", data.petEnergy)
                put("petMood", data.petMood)
            }

            val existingId = preferences.getBmobPetDataId()
            val apiResult = if (existingId.isNotEmpty()) {
                apiClient.updateRow(TABLE_PET_DATA, existingId, json)
            } else {
                apiClient.createRow(TABLE_PET_DATA, json)
            }

            when (apiResult) {
                is ApiResult.Success -> {
                    val objectId = apiResult.data.optString(
                        "objectId",
                        existingId
                    )
                    if (existingId.isEmpty() && objectId.isNotEmpty()) {
                        preferences.saveBmobPetDataId(objectId)
                    }
                    PetLogger.i(TAG, "宠物数据已同步到云端: $objectId")
                    Result.Success(objectId)
                }
                is ApiResult.HttpError ->
                    Result.Error(Exception("上传失败(${apiResult.code}): ${apiResult.message}"))
                is ApiResult.NetworkError ->
                    Result.Error(Exception("网络连接失败", apiResult.cause))
                is ApiResult.ParseError ->
                    Result.Error(Exception("数据解析失败", apiResult.cause))
            }
        }

    override suspend fun downloadPetData(userId: String): Result<CloudPetData?> =
        withContext(Dispatchers.IO) {
            val where = JSONObject().put("userId", userId)
            when (val result = apiClient.queryRows(TABLE_PET_DATA, where)) {
                is ApiResult.Success -> {
                    val arr = result.data
                    if (arr.length() == 0) {
                        return@withContext Result.Success(null)
                    }
                    val json = arr.getJSONObject(0)
                    val petData = CloudPetData(
                        objectId = json.optString("objectId"),
                        userId = json.optString("userId"),
                        petX = json.optInt("petX"),
                        petY = json.optInt("petY"),
                        emotion = json.optInt("emotion", 5),
                        bondLevel = json.optInt("bondLevel"),
                        totalOnlineMinutes = json.optLong("totalOnlineMinutes"),
                        totalChatCount = json.optInt("totalChatCount"),
                        familiarityDays = json.optInt("familiarityDays"),
                        petTrust = json.optInt("petTrust", 50),
                        petEnergy = json.optInt("petEnergy", 70),
                        petMood = json.optString("petMood", "CALM"),
                        updatedAt = json.optString("updatedAt")
                    )
                    preferences.saveBmobPetDataId(petData.objectId)
                    PetLogger.i(TAG, "已从云端拉取宠物数据: ${petData.objectId}")
                    Result.Success(petData)
                }
                is ApiResult.HttpError ->
                    Result.Error(Exception("查询失败(${result.code}): ${result.message}"))
                is ApiResult.NetworkError ->
                    Result.Error(Exception("网络连接失败", result.cause))
                is ApiResult.ParseError ->
                    Result.Error(Exception("数据解析失败", result.cause))
            }
        }

    // ── 内部工具 ─────────────────────────────────────────────────────

    private fun saveSession(user: BmobUser) {
        preferences.saveBmobSessionToken(user.sessionToken)
        preferences.saveBmobUserId(user.objectId)
        preferences.saveBmobUsername(user.username)
        apiClient.sessionToken = user.sessionToken
    }
}

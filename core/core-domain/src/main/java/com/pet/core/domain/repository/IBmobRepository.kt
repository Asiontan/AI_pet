package com.pet.core.domain.repository

import com.pet.core.common.result.Result
import com.pet.core.domain.model.BmobUser
import com.pet.core.domain.model.CloudPetData

interface IBmobRepository {

    /** 用户注册 */
    suspend fun register(username: String, password: String): Result<BmobUser>

    /** 用户登录 */
    suspend fun login(username: String, password: String): Result<BmobUser>

    /** 获取当前登录用户信息，未登录则返回 null */
    suspend fun getCurrentUser(): Result<BmobUser?>

    /** 注销登录 */
    suspend fun logout(): Result<Unit>

    /** 将本地宠物数据上传/更新到云端 */
    suspend fun uploadPetData(data: CloudPetData): Result<String>

    /** 从云端拉取宠物数据 */
    suspend fun downloadPetData(userId: String): Result<CloudPetData?>

    /** 检查当前是否已登录 */
    fun isLoggedIn(): Boolean
}

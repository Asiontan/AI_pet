package com.pet.core.data.network

import com.pet.core.common.logger.PetLogger
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bmob REST API 客户端。
 *
 * 所有请求自动携带 Bmob 鉴权头（Application-Id / REST-API-Key），
 * 登录后的请求额外携带 Session-Token。
 *
 * @see <a href="https://doc.bmobapp.com/cloud_function/restful/">Bmob REST API 文档</a>
 */
class BmobApiClient(
    private val appId: String,
    private val restApiKey: String
) : BaseApiClient(BASE_URL) {

    companion object {
        private const val TAG = "BmobApiClient"
        private const val BASE_URL = "https://api2.bmob.cn/1"
    }

    var sessionToken: String = ""

    private fun bmobHeaders(): Map<String, String> = buildMap {
        put("X-Bmob-Application-Id", appId)
        put("X-Bmob-REST-API-Key", restApiKey)
        if (sessionToken.isNotEmpty()) {
            put("X-Bmob-Session-Token", sessionToken)
        }
    }

    // ── 用户 ───────────────────────────────────────────────────────────

    /**
     * 注册新用户。
     * @return 包含 objectId / sessionToken / createdAt 的 JSON 字符串
     */
    suspend fun registerUser(
        username: String,
        password: String
    ): ApiResult<JSONObject> {
        val body = JSONObject().apply {
            put("username", username)
            put("password", password)
        }
        return postJson("/users", body.toString(), bmobHeaders()) { JSONObject(it) }
    }

    /**
     * 用户登录。
     * @return 包含 objectId / username / sessionToken 等字段的 JSON
     */
    suspend fun loginUser(
        username: String,
        password: String
    ): ApiResult<JSONObject> {
        val encodedUsername = java.net.URLEncoder.encode(username, "UTF-8")
        val encodedPassword = java.net.URLEncoder.encode(password, "UTF-8")
        return get(
            "/login?username=$encodedUsername&password=$encodedPassword",
            bmobHeaders()
        ) { JSONObject(it) }
    }

    /**
     * 获取当前登录用户的信息。
     * 需要 sessionToken 已设置。
     */
    suspend fun getCurrentUser(objectId: String): ApiResult<JSONObject> {
        return get("/users/$objectId", bmobHeaders()) { JSONObject(it) }
    }

    // ── 数据表操作 ──────────────────────────────────────────────────────

    /**
     * 向指定表插入一条数据。
     * @param className Bmob 表名
     * @param data      要写入的 JSON 数据
     * @return 包含 objectId / createdAt 的响应
     */
    suspend fun createRow(
        className: String,
        data: JSONObject
    ): ApiResult<JSONObject> {
        return postJson("/classes/$className", data.toString(), bmobHeaders()) {
            JSONObject(it)
        }
    }

    /**
     * 更新指定表的一条数据。
     */
    suspend fun updateRow(
        className: String,
        objectId: String,
        data: JSONObject
    ): ApiResult<JSONObject> {
        return putJson("/classes/$className/$objectId", data.toString(), bmobHeaders()) {
            JSONObject(it)
        }
    }

    /**
     * 查询指定表的数据。
     * @param where BQL 查询条件 JSON（可选）
     */
    suspend fun queryRows(
        className: String,
        where: JSONObject? = null
    ): ApiResult<JSONArray> {
        val path = buildString {
            append("/classes/$className")
            if (where != null) {
                val encoded = java.net.URLEncoder.encode(where.toString(), "UTF-8")
                append("?where=$encoded")
            }
        }
        return get(path, bmobHeaders()) { JSONObject(it).getJSONArray("results") }
    }

    /**
     * 删除指定表的一条数据。
     */
    suspend fun deleteRow(
        className: String,
        objectId: String
    ): ApiResult<String> {
        return delete("/classes/$className/$objectId", bmobHeaders())
    }
}

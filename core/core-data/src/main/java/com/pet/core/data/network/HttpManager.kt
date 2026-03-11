package com.pet.core.data.network

import com.pet.core.common.logger.PetLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * 全局 HTTP 请求管理器。
 *
 * 直接通过静态方法发起请求，无需继承，适合一次性或简单场景。
 *
 * 用法示例：
 * ```kotlin
 * // GET
 * val result = HttpManager.get("https://api.example.com/pet/info")
 *
 * // POST JSON
 * val result = HttpManager.postJson(
 *     url = "https://api.example.com/pet/update",
 *     jsonBody = """{"id":"1","name":"Cecilia"}"""
 * )
 *
 * // 带自定义解析
 * val result = HttpManager.get("https://api.example.com/pet/info") { body ->
 *     Gson().fromJson(body, PetInfo::class.java)
 * }
 *
 * // 处理结果
 * when (result) {
 *     is ApiResult.Success      -> use(result.data)
 *     is ApiResult.HttpError    -> log(result.code, result.message)
 *     is ApiResult.NetworkError -> log(result.cause)
 *     is ApiResult.ParseError   -> log(result.cause)
 * }
 * ```
 */
object HttpManager {

    private const val TAG = "HttpManager"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client get() = HttpClient.instance

    // ----------------------------------------------------------------
    // GET
    // ----------------------------------------------------------------

    /**
     * 发起 GET 请求，返回原始响应字符串。
     *
     * @param url     完整请求地址
     * @param headers 额外请求头（可选）
     */
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): ApiResult<String> = get(url, headers) { it }

    /**
     * 发起 GET 请求，并用 [parse] 将响应体解析为 [T]。
     */
    suspend fun <T> get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        parse: (String) -> T
    ): ApiResult<T> {
        val request = Request.Builder()
            .url(url)
            .get()
            .applyHeaders(headers)
            .build()
        return execute(request, parse)
    }

    // ----------------------------------------------------------------
    // POST 表单
    // ----------------------------------------------------------------

    /**
     * 发起 POST 表单请求，返回原始响应字符串。
     *
     * @param url     完整请求地址
     * @param params  表单参数
     * @param headers 额外请求头（可选）
     */
    suspend fun post(
        url: String,
        params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap()
    ): ApiResult<String> = post(url, params, headers) { it }

    /**
     * 发起 POST 表单请求，并用 [parse] 将响应体解析为 [T]。
     */
    suspend fun <T> post(
        url: String,
        params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        parse: (String) -> T
    ): ApiResult<T> {
        val body = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .post(body)
            .applyHeaders(headers)
            .build()
        return execute(request, parse)
    }

    // ----------------------------------------------------------------
    // POST JSON
    // ----------------------------------------------------------------

    /**
     * 发起 POST JSON 请求，返回原始响应字符串。
     *
     * @param url      完整请求地址
     * @param jsonBody JSON 字符串
     * @param headers  额外请求头（可选）
     */
    suspend fun postJson(
        url: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap()
    ): ApiResult<String> = postJson(url, jsonBody, headers) { it }

    /**
     * 发起 POST JSON 请求，并用 [parse] 将响应体解析为 [T]。
     */
    suspend fun <T> postJson(
        url: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap(),
        parse: (String) -> T
    ): ApiResult<T> {
        val body = jsonBody.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .applyHeaders(headers)
            .build()
        return execute(request, parse)
    }

    // ----------------------------------------------------------------
    // 内部执行
    // ----------------------------------------------------------------

    private suspend fun <T> execute(
        request: Request,
        parse: (String) -> T
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                try {
                    ApiResult.Success(parse(bodyStr))
                } catch (e: Exception) {
                    PetLogger.e(TAG, "Parse error", e)
                    ApiResult.ParseError(e)
                }
            } else {
                PetLogger.w(TAG, "HTTP ${response.code}: ${response.message}")
                ApiResult.HttpError(response.code, response.message)
            }
        } catch (e: IOException) {
            PetLogger.e(TAG, "Network error", e)
            ApiResult.NetworkError(e)
        } catch (e: Exception) {
            PetLogger.e(TAG, "Unexpected error", e)
            ApiResult.NetworkError(e)
        }
    }

    // ----------------------------------------------------------------
    // 工具
    // ----------------------------------------------------------------

    private fun Request.Builder.applyHeaders(headers: Map<String, String>): Request.Builder {
        headers.forEach { (k, v) -> header(k, v) }
        return this
    }
}


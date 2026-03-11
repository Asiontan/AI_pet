package com.pet.core.data.network

import com.pet.core.common.logger.PetLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * 网络请求基类。
 *
 * 子类只需继承此类，传入 [baseUrl]，然后调用 [get] / [post] / [postJson] 即可。
 *
 * 示例：
 * ```kotlin
 * class PetApiClient : BaseApiClient("https://api.example.com") {
 *
 *     suspend fun fetchPetInfo(id: String): ApiResult<String> =
 *         get("/pet/$id")
 *
 *     suspend fun updatePet(json: String): ApiResult<String> =
 *         postJson("/pet/update", json)
 * }
 * ```
 */
abstract class BaseApiClient(
    protected val baseUrl: String,
    private val client: okhttp3.OkHttpClient = HttpClient.instance
) {

    private val tag = this::class.simpleName ?: "BaseApiClient"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // ----------------------------------------------------------------
    // 公开请求方法
    // ----------------------------------------------------------------

    /**
     * GET 请求
     *
     * @param path     相对路径，例如 "/pet/info"
     * @param headers  额外请求头（可选）
     * @param parse    将响应体字符串解析为目标类型的函数
     */
    suspend fun <T> get(
        path: String,
        headers: Map<String, String> = emptyMap(),
        parse: (String) -> T
    ): ApiResult<T> {
        val request = Request.Builder()
            .url(baseUrl + path)
            .get()
            .applyHeaders(headers)
            .build()
        return execute(request, parse)
    }

    /**
     * GET 请求（返回原始字符串，无需自定义解析）
     */
    suspend fun get(
        path: String,
        headers: Map<String, String> = emptyMap()
    ): ApiResult<String> = get(path, headers) { it }

    /**
     * POST 表单请求
     *
     * @param path   相对路径
     * @param params 表单参数
     * @param parse  解析函数
     */
    suspend fun <T> post(
        path: String,
        params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        parse: (String) -> T
    ): ApiResult<T> {
        val body = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
        }.build()

        val request = Request.Builder()
            .url(baseUrl + path)
            .post(body)
            .applyHeaders(headers)
            .build()
        return execute(request, parse)
    }

    /**
     * POST JSON 请求
     *
     * @param path    相对路径
     * @param jsonBody JSON 字符串
     * @param parse   解析函数
     */
    suspend fun <T> postJson(
        path: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap(),
        parse: (String) -> T
    ): ApiResult<T> {
        val body = jsonBody.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(baseUrl + path)
            .post(body)
            .applyHeaders(headers)
            .build()
        return execute(request, parse)
    }

    /**
     * POST JSON 请求（返回原始字符串）
     */
    suspend fun postJson(
        path: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap()
    ): ApiResult<String> = postJson(path, jsonBody, headers) { it }

    // ----------------------------------------------------------------
    // 内部执行逻辑
    // ----------------------------------------------------------------

    /**
     * 在 IO 线程执行请求，统一处理异常并包装为 [ApiResult]。
     */
    private suspend fun <T> execute(
        request: Request,
        parse: (String) -> T
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            val response: Response = client.newCall(request).execute()
            handleResponse(response, parse)
        } catch (e: IOException) {
            PetLogger.e(tag, "Network error: ${e.message}", e)
            ApiResult.NetworkError(e)
        } catch (e: Exception) {
            PetLogger.e(tag, "Unexpected error: ${e.message}", e)
            ApiResult.NetworkError(e)
        }
    }

    private fun <T> handleResponse(
        response: Response,
        parse: (String) -> T
    ): ApiResult<T> {
        return if (response.isSuccessful) {
            val bodyStr = response.body?.string() ?: ""
            try {
                ApiResult.Success(parse(bodyStr))
            } catch (e: Exception) {
                PetLogger.e(tag, "Parse error: ${e.message}", e)
                ApiResult.ParseError(e)
            }
        } else {
            PetLogger.w(tag, "HTTP error: ${response.code} ${response.message}")
            ApiResult.HttpError(response.code, response.message)
        }
    }

    // ----------------------------------------------------------------
    // 工具扩展
    // ----------------------------------------------------------------

    private fun Request.Builder.applyHeaders(headers: Map<String, String>): Request.Builder {
        headers.forEach { (k, v) -> header(k, v) }
        return this
    }
}


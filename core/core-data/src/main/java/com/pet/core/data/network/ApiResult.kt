package com.pet.core.data.network

/**
 * 网络请求的统一结果封装。
 *
 * @param T 成功时的数据类型
 */
sealed class ApiResult<out T> {

    /** 请求成功，携带解析后的数据 */
    data class Success<T>(val data: T) : ApiResult<T>()

    /** 服务端返回了非 2xx 状态码 */
    data class HttpError(
        val code: Int,
        val message: String
    ) : ApiResult<Nothing>()

    /** 网络异常（无网络、超时、DNS 失败等） */
    data class NetworkError(val cause: Throwable) : ApiResult<Nothing>()

    /** 数据解析异常 */
    data class ParseError(val cause: Throwable) : ApiResult<Nothing>()

    // ----------------------------------------------------------------
    // 便捷扩展
    // ----------------------------------------------------------------

    val isSuccess get() = this is Success

    fun getOrNull(): T? = if (this is Success) data else null

    fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Success     -> Success(transform(data))
        is HttpError   -> this
        is NetworkError -> this
        is ParseError  -> this
    }
}


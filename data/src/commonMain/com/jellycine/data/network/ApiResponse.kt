package com.jellycine.data.network

class ApiResponse<T>(
    private val bodyValue: T?,
    private val statusCode: Int,
    private val statusMessage: String = "",
    private val headerValues: ApiHeaders = ApiHeaders.Empty,
    private val errorBodyValue: String? = null
) {
    val isSuccessful: Boolean
        get() = statusCode in 200..299

    fun body(): T? = bodyValue

    fun code(): Int = statusCode

    fun message(): String = statusMessage

    fun headers(): ApiHeaders = headerValues

    fun errorBody(): String? = errorBodyValue
}

class HttpStatusException(
    val statusCode: Int,
    message: String,
    val statusMessage: String? = null
) : Exception(message)

fun Throwable?.isAuthenticationStatusFailure(): Boolean {
    val statusCode = (this as? HttpStatusException)?.statusCode ?: return false
    return statusCode == 401 || statusCode == 403
}
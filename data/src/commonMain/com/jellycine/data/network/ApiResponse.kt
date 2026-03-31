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

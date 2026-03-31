package com.jellycine.data.network

class ApiHeaders private constructor(
    private val values: Map<String, List<String>>
) {
    fun get(name: String): String? {
        return values[name.lowercase()]?.firstOrNull()
    }

    companion object {
        val Empty = ApiHeaders(emptyMap())

        fun from(values: Map<String, List<String>>): ApiHeaders {
            return ApiHeaders(
                values.entries.associate { entry ->
                    entry.key.lowercase() to entry.value
                }
            )
        }
    }
}

package com.jellycine.data.util

import android.net.Uri
import com.jellycine.data.network.trimTrailingSlash
import java.net.URI

internal fun buildServerUrl(
    baseUrl: String,
    encodedPath: String,
    queryParams: List<Pair<String, String?>> = emptyList()
): String {
    val builder = Uri.parse(trimTrailingSlash(baseUrl))
        .buildUpon()
        .appendEncodedPath(encodedPath.trimStart('/'))
    queryParams.forEach { (key, value) ->
        if (!value.isNullOrBlank()) {
            builder.appendQueryParameter(key, value)
        }
    }
    return builder.build().toString()
}

internal fun getServerUrl(
    baseUrl: String,
    url: String?
): String? {
    val rawUrl = url?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        URI.create(trimTrailingSlash(baseUrl, trailingSlash = true))
            .resolve(rawUrl)
            .toString()
    }.getOrNull()
}

internal fun removeQueryParameter(
    url: String,
    queryParamName: String
): String {
    val sourceUri = Uri.parse(url)
    val hasQueryParam = sourceUri.queryParameterNames.any { queryName ->
        queryName.equals(queryParamName, ignoreCase = true)
    }
    if (!hasQueryParam) {
        return url
    }

    val builder = sourceUri.buildUpon().clearQuery()
    sourceUri.queryParameterNames
        .filterNot { queryName -> queryName.equals(queryParamName, ignoreCase = true) }
        .forEach { queryName ->
            sourceUri.getQueryParameters(queryName).forEach { queryValue ->
                builder.appendQueryParameter(queryName, queryValue)
            }
        }

    return builder.build().toString()
}
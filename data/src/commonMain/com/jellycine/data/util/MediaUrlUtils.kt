package com.jellycine.data.util

import com.jellycine.data.network.trimTrailingSlash
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendEncodedPathSegments
import io.ktor.http.encodedPath
import io.ktor.http.parseQueryString

internal fun buildServerUrl(
    baseUrl: String,
    encodedPath: String,
    queryParams: List<Pair<String, String?>> = emptyList()
): String {
    val builder = URLBuilder(trimTrailingSlash(baseUrl))
    val path = encodedPath.trimStart('/')
    builder.appendEncodedPathSegments(path.substringBefore('?'))
    val embeddedQuery = path.substringAfter('?', "")
    if (embeddedQuery.isNotEmpty()) {
        builder.encodedParameters.appendAll(parseQueryString(embeddedQuery, decode = false))
    }
    queryParams.forEach { (key, value) ->
        if (!value.isNullOrBlank()) {
            builder.parameters.append(key, value)
        }
    }
    return builder.buildString()
}

private val schemePattern = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

internal fun getServerUrl(
    baseUrl: String,
    url: String?
): String? {
    val rawUrl = url?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        val base = trimTrailingSlash(baseUrl, trailingSlash = true)
        val baseParsed = Url(base)
        val origin = URLBuilder(base).apply {
            encodedPath = ""
            parameters.clear()
            fragment = ""
        }.buildString().trimEnd('/')
        when {
            schemePattern.containsMatchIn(rawUrl) -> rawUrl
            rawUrl.startsWith("//") -> "${baseParsed.protocol.name}:$rawUrl"
            rawUrl.startsWith("/") -> origin + rawUrl
            else -> base + rawUrl.removePrefix("./")
        }
    }.getOrNull()
}

internal fun removeQueryParameter(
    url: String,
    queryParamName: String
): String {
    val builder = URLBuilder(url)
    val names = builder.parameters.names().filter { it.equals(queryParamName, ignoreCase = true) }
    if (names.isEmpty()) return url
    names.forEach { builder.parameters.remove(it) }
    return builder.buildString()
}

internal fun String.queryParameter(name: String): String? = Url(this).parameters[name]

internal fun String.withQueryParameter(name: String, value: String): String {
    return URLBuilder(this).apply { parameters.append(name, value) }.buildString()
}

fun String.toGuid(): String {
    if (this.contains("-") || this.length != 32) return this
    return try {
        StringBuilder(this)
            .insert(8, "-")
            .insert(13, "-")
            .insert(18, "-")
            .insert(23, "-")
            .toString()
    } catch (_: Exception) {
        this
    }
}

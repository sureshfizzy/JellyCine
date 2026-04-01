package com.jellycine.data.network

fun trimTrailingSlash(url: String, trailingSlash: Boolean = false): String {
    var normalized = url
    while (normalized.endsWith("/")) {
        normalized = normalized.dropLast(1)
    }
    return if (trailingSlash) "$normalized/" else normalized
}

fun canonicalServerUrl(url: String): String {
    return trimTrailingSlash(url.trim())
}

fun canonicalServerUrlKey(url: String): String {
    return canonicalServerUrl(url).lowercase()
}

fun sameServerUrl(left: String?, right: String?): Boolean {
    if (left.isNullOrBlank() || right.isNullOrBlank()) return false
    return canonicalServerUrl(left).equals(canonicalServerUrl(right), ignoreCase = true)
}

fun buildBaseUrlCandidates(serverUrl: String): List<String> {
    val normalized = trimTrailingSlash(serverUrl.trim())
    if (normalized.endsWith("/emby", ignoreCase = true)) {
        return listOf(normalized)
    }

    return listOf(normalized, "$normalized/emby")
}
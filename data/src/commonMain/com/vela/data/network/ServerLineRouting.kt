package com.vela.data.network

enum class NetworkAccess {
    OFFLINE,
    LAN_CAPABLE,
    WAN
}

enum class ServerLineSwitchReason {
    NETWORK,
    FAILOVER
}

data class ServerLineSwitchEvent(
    val serverId: String,
    val customName: String,
    val isLan: Boolean,
    val reason: ServerLineSwitchReason
)

data class RoutableLine(
    val id: String,
    val isLan: Boolean
)

fun hostFromUrl(url: String): String {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return ""
    val afterScheme = trimmed.substringAfter("://", trimmed)
    val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    if (authority.startsWith("[")) {
        val end = authority.indexOf(']')
        if (end > 1) return authority.substring(1, end).lowercase()
    }
    val host = authority.substringBefore('%').substringBefore(':').trim()
    return host.lowercase()
}

fun isLanHost(host: String): Boolean {
    val value = host.trim().lowercase()
    if (value.isEmpty()) return false
    if (value == "localhost" || value == "::1" || value == "0:0:0:0:0:0:0:1") return true
    if (value.endsWith(".local") || value.endsWith(".lan")) return true
    if (value.startsWith("192.168.") || value.startsWith("10.")) return true
    if (value.startsWith("172.")) {
        val second = value.split('.').getOrNull(1)?.toIntOrNull() ?: return false
        return second in 16..31
    }
    return false
}

fun preferLan(access: NetworkAccess): Boolean = access == NetworkAccess.LAN_CAPABLE

fun requestMatchesServerUrl(requestUrl: String, serverUrl: String): Boolean {
    val request = canonicalServerUrl(requestUrl)
    if (request.isBlank() || serverUrl.isBlank()) return false
    return buildBaseUrlCandidates(serverUrl).any { candidate ->
        val base = canonicalServerUrl(candidate)
        request.equals(base, ignoreCase = true) ||
            request.startsWith("$base/", ignoreCase = true)
    }
}

fun pickPreferredReachableLine(
    lines: List<RoutableLine>,
    reachableIds: Set<String>,
    preferLan: Boolean,
    currentId: String?
): RoutableLine? {
    if (lines.isEmpty() || reachableIds.isEmpty()) return null
    val reachable = lines.filter { it.id in reachableIds }
    if (reachable.isEmpty()) return null
    val preferred = reachable.filter { it.isLan == preferLan }
    val pool = preferred.ifEmpty { reachable }
    return pool.firstOrNull { it.id == currentId } ?: pool.first()
}

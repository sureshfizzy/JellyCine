package com.vela.data.update

import com.vela.data.network.VelaJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val GITHUB_OWNER = "ZeroDevi1"
const val GITHUB_REPO = "Vela"
const val GITHUB_LATEST_RELEASE_URL =
    "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

enum class AppFlavor(val fileToken: String) {
    Phone("phone"),
    Tv("tv");

    companion object {
        fun fromPackageName(packageName: String): AppFlavor {
            return if (packageName == "com.vela.tv") Tv else Phone
        }

        fun fromFileToken(token: String): AppFlavor? {
            return entries.firstOrNull { it.fileToken.equals(token, ignoreCase = true) }
        }
    }
}

data class DownloadMirror(
    val id: String,
    val label: String,
    val prefix: String
)

data class AppUpdateAsset(
    val name: String,
    val flavor: AppFlavor,
    val abi: String?,
    val sizeBytes: Long,
    val downloadUrl: String
)

data class AppUpdateRelease(
    val tagName: String,
    val versionName: String,
    val title: String,
    val notes: String,
    val htmlUrl: String,
    val assets: List<AppUpdateAsset>
)

@Serializable
internal data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val assets: List<GithubAssetDto> = emptyList()
)

@Serializable
internal data class GithubAssetDto(
    val name: String,
    val size: Long = 0,
    @SerialName("browser_download_url") val browserDownloadUrl: String
)

val BuiltinDownloadMirrors: List<DownloadMirror> = listOf(
    DownloadMirror(id = "direct", label = "GitHub", prefix = ""),
    DownloadMirror(id = "gh-proxy", label = "gh-proxy.com", prefix = "https://gh-proxy.com/"),
    DownloadMirror(id = "ghfast", label = "ghfast.top", prefix = "https://ghfast.top/"),
    DownloadMirror(id = "ghproxy-net", label = "ghproxy.net", prefix = "https://ghproxy.net/"),
    DownloadMirror(id = "mirror-ghproxy", label = "mirror.ghproxy.com", prefix = "https://mirror.ghproxy.com/")
)

const val DEFAULT_DOWNLOAD_MIRROR_ID = "gh-proxy"
const val CUSTOM_DOWNLOAD_MIRROR_ID = "custom"

private val AssetWithAbi = Regex(
    """^vela-(phone|tv)-release-(.+?)-(armeabi-v7a|arm64-v8a|x86_64|x86)\.apk$""",
    RegexOption.IGNORE_CASE
)
private val AssetWithoutAbi = Regex(
    """^vela-(phone|tv)-release-(.+)\.apk$""",
    RegexOption.IGNORE_CASE
)

fun accelerateGithubUrl(url: String, prefix: String): String {
    val trimmedPrefix = prefix.trim().trimEnd('/')
    if (trimmedPrefix.isEmpty()) return url
    if (url.startsWith(trimmedPrefix)) return url
    return "$trimmedPrefix/$url"
}

fun sanitizeMirrorPrefix(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    if (!trimmed.startsWith("https://", ignoreCase = true)) return null
    return trimmed.trimEnd('/') + "/"
}

fun versionNameFromTag(tagName: String): String {
    return tagName.trim().removePrefix("v").removePrefix("V")
}

fun compareVersions(left: String, right: String): Int {
    val a = versionParts(left)
    val b = versionParts(right)
    val size = maxOf(a.size, b.size)
    for (index in 0 until size) {
        val delta = a.getOrElse(index) { 0 } - b.getOrElse(index) { 0 }
        if (delta != 0) return delta
    }
    return 0
}

fun isNewerVersion(latest: String, current: String): Boolean {
    return compareVersions(latest, current) > 0
}

fun parseGithubReleaseJson(json: String): AppUpdateRelease {
    val dto = VelaJson.decodeFromString(GithubReleaseDto.serializer(), json)
    val versionName = versionNameFromTag(dto.tagName)
    return AppUpdateRelease(
        tagName = dto.tagName,
        versionName = versionName,
        title = dto.name?.takeIf { it.isNotBlank() } ?: dto.tagName,
        notes = dto.body.orEmpty().trim(),
        htmlUrl = dto.htmlUrl,
        assets = dto.assets.mapNotNull(::parseGithubAsset)
    )
}

fun parseUpdateAssetName(name: String, sizeBytes: Long, downloadUrl: String): AppUpdateAsset? {
    val withAbi = AssetWithAbi.matchEntire(name)
    if (withAbi != null) {
        val flavor = AppFlavor.fromFileToken(withAbi.groupValues[1]) ?: return null
        return AppUpdateAsset(
            name = name,
            flavor = flavor,
            abi = withAbi.groupValues[3].lowercase(),
            sizeBytes = sizeBytes,
            downloadUrl = downloadUrl
        )
    }
    val withoutAbi = AssetWithoutAbi.matchEntire(name) ?: return null
    val flavor = AppFlavor.fromFileToken(withoutAbi.groupValues[1]) ?: return null
    return AppUpdateAsset(
        name = name,
        flavor = flavor,
        abi = null,
        sizeBytes = sizeBytes,
        downloadUrl = downloadUrl
    )
}

fun assetsForFlavor(assets: List<AppUpdateAsset>, flavor: AppFlavor): List<AppUpdateAsset> {
    return assets.filter { it.flavor == flavor }
}

fun pickRecommendedAsset(
    assets: List<AppUpdateAsset>,
    flavor: AppFlavor,
    supportedAbis: List<String>
): AppUpdateAsset? {
    val matchingFlavor = assetsForFlavor(assets, flavor)
    if (matchingFlavor.isEmpty()) return null
    for (abi in supportedAbis) {
        matchingFlavor.firstOrNull { it.abi.equals(abi, ignoreCase = true) }?.let { return it }
    }
    matchingFlavor.firstOrNull { it.abi == null }?.let { return it }
    return matchingFlavor.firstOrNull()
}

fun resolveDownloadMirror(
    id: String,
    customPrefix: String,
    builtins: List<DownloadMirror> = BuiltinDownloadMirrors
): DownloadMirror {
    if (id == CUSTOM_DOWNLOAD_MIRROR_ID) {
        val prefix = sanitizeMirrorPrefix(customPrefix).orEmpty()
        return DownloadMirror(
            id = CUSTOM_DOWNLOAD_MIRROR_ID,
            label = "Custom",
            prefix = prefix
        )
    }
    return builtins.firstOrNull { it.id == id } ?: builtins.first { it.id == DEFAULT_DOWNLOAD_MIRROR_ID }
}

private fun parseGithubAsset(dto: GithubAssetDto): AppUpdateAsset? {
    return parseUpdateAssetName(dto.name, dto.size, dto.browserDownloadUrl)
}

private fun versionParts(raw: String): List<Int> {
    val core = versionNameFromTag(raw).substringBefore('-').substringBefore('+')
    return core.split('.').map { part ->
        part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
    }
}

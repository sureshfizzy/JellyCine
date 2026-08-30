package com.vela.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {

    @Test
    fun parseAssetNameWithAbiAndFlavor() {
        val asset = parseUpdateAssetName(
            name = "vela-phone-release-1.2.3-arm64-v8a.apk",
            sizeBytes = 10,
            downloadUrl = "https://github.com/ZeroDevi1/Vela/releases/download/v1.2.3/vela-phone-release-1.2.3-arm64-v8a.apk"
        )
        requireNotNull(asset)
        assertEquals(AppFlavor.Phone, asset.flavor)
        assertEquals("arm64-v8a", asset.abi)
    }

    @Test
    fun parseTvAssetAndUniversal() {
        val tv = parseUpdateAssetName(
            name = "vela-tv-release-1.0.0-armeabi-v7a.apk",
            sizeBytes = 1,
            downloadUrl = "https://example/tv.apk"
        )
        requireNotNull(tv)
        assertEquals(AppFlavor.Tv, tv.flavor)
        assertEquals("armeabi-v7a", tv.abi)

        val universal = parseUpdateAssetName(
            name = "vela-phone-release-1.0.0.apk",
            sizeBytes = 1,
            downloadUrl = "https://example/phone.apk"
        )
        requireNotNull(universal)
        assertNull(universal.abi)
    }

    @Test
    fun ignoreNonReleaseApkNames() {
        assertNull(parseUpdateAssetName("app-release.apk", 1, "https://example/a.apk"))
        assertNull(parseUpdateAssetName("notes.txt", 1, "https://example/notes.txt"))
    }

    @Test
    fun parseGithubReleaseJsonMapsAssets() {
        val json = """
            {
              "tag_name": "v1.0.1",
              "name": "Vela 1.0.1",
              "body": "fixes",
              "html_url": "https://github.com/ZeroDevi1/Vela/releases/tag/v1.0.1",
              "assets": [
                {
                  "name": "vela-phone-release-1.0.1-arm64-v8a.apk",
                  "size": 100,
                  "browser_download_url": "https://github.com/ZeroDevi1/Vela/releases/download/v1.0.1/vela-phone-release-1.0.1-arm64-v8a.apk"
                },
                {
                  "name": "vela-tv-release-1.0.1-x86_64.apk",
                  "size": 80,
                  "browser_download_url": "https://github.com/ZeroDevi1/Vela/releases/download/v1.0.1/vela-tv-release-1.0.1-x86_64.apk"
                }
              ]
            }
        """.trimIndent()
        val release = parseGithubReleaseJson(json)
        assertEquals("1.0.1", release.versionName)
        assertEquals(2, release.assets.size)
        assertTrue(release.assets.any { it.flavor == AppFlavor.Phone && it.abi == "arm64-v8a" })
        assertTrue(release.assets.any { it.flavor == AppFlavor.Tv && it.abi == "x86_64" })
    }

    @Test
    fun versionCompareHandlesTagPrefix() {
        assertTrue(isNewerVersion("v1.0.1", "1.0.0"))
        assertFalse(isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(isNewerVersion("1.0.0", "1.0.1"))
        assertTrue(compareVersions("1.10.0", "1.9.0") > 0)
    }

    @Test
    fun pickRecommendedPrefersDeviceAbiThenUniversal() {
        val arm = asset(AppFlavor.Phone, "arm64-v8a")
        val v7 = asset(AppFlavor.Phone, "armeabi-v7a")
        val tv = asset(AppFlavor.Tv, "arm64-v8a")
        val picked = pickRecommendedAsset(
            assets = listOf(v7, tv, arm),
            flavor = AppFlavor.Phone,
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a")
        )
        assertEquals("arm64-v8a", picked?.abi)

        val universal = asset(AppFlavor.Phone, null)
        val fallback = pickRecommendedAsset(
            assets = listOf(universal, v7),
            flavor = AppFlavor.Phone,
            supportedAbis = listOf("x86_64")
        )
        assertNull(fallback?.abi)
    }

    @Test
    fun accelerateGithubUrlPrefixesOnce() {
        val url = "https://github.com/ZeroDevi1/Vela/releases/download/v1.0.0/app.apk"
        val prefix = "https://gh-proxy.com/"
        val accelerated = accelerateGithubUrl(url, prefix)
        assertEquals("https://gh-proxy.com/$url", accelerated)
        assertEquals(accelerated, accelerateGithubUrl(accelerated, prefix.trimEnd('/')))
        assertEquals(url, accelerateGithubUrl(url, ""))
    }

    @Test
    fun sanitizeMirrorPrefixRequiresHttps() {
        assertEquals("https://gh-proxy.com/", sanitizeMirrorPrefix("https://gh-proxy.com"))
        assertNull(sanitizeMirrorPrefix("http://insecure.example"))
        assertEquals("", sanitizeMirrorPrefix("   "))
    }

    @Test
    fun flavorFromPackageName() {
        assertEquals(AppFlavor.Tv, AppFlavor.fromPackageName("com.vela.tv"))
        assertEquals(AppFlavor.Phone, AppFlavor.fromPackageName("com.vela.app"))
    }

    private fun asset(flavor: AppFlavor, abi: String?): AppUpdateAsset {
        val name = if (abi == null) {
            "vela-${flavor.fileToken}-release-1.0.0.apk"
        } else {
            "vela-${flavor.fileToken}-release-1.0.0-$abi.apk"
        }
        return AppUpdateAsset(name, flavor, abi, 1, "https://example/$name")
    }
}

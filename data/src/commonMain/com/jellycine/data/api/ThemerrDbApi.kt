package com.jellycine.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ThemerrThemeResponse(
    @SerialName("youtube_theme_url") val youtubeThemeUrl: String? = null
)

internal class ThemerrDbApi(private val client: HttpClient) {

    suspend fun youtubeThemeUrl(mediaType: String, database: String, id: String): String? =
        runCatching {
            client.get("$BASE_URL/$mediaType/$database/$id.json")
                .body<ThemerrThemeResponse>()
                .youtubeThemeUrl
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()

    private companion object {
        private const val BASE_URL = "https://app.lizardbyte.dev/ThemerrDB"
    }
}
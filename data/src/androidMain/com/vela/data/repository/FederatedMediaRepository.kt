package com.vela.data.repository

import android.content.Context
import com.vela.data.api.MediaServerApi
import com.vela.data.model.BaseItemDto
import com.vela.data.model.SearchMediaType
import com.vela.data.network.NetworkModule
import com.vela.data.network.ServerType
import com.vela.data.network.trimTrailingSlash
import com.vela.data.preferences.NetworkPreferences
import com.vela.data.security.SecureSessionStore
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class FederatedServer(
    val id: String,
    val name: String
)

data class FederatedMediaItem(
    val item: BaseItemDto,
    val serverId: String,
    val serverName: String,
    val imageUrl: String?
)

data class FederatedServerFailure(
    val serverId: String,
    val serverName: String,
    val message: String
)

data class FederatedMediaResponse(
    val items: List<FederatedMediaItem>,
    val failures: List<FederatedServerFailure>
)

enum class FederatedContentSection {
    CONTINUE_WATCHING,
    FAVORITES,
    LIBRARIES
}

/**
 * 使用每个已保存账户自己的 URL、token 和 userId 读取聚合内容，避免通过切换活动会话制造全局状态竞争。
 */
class FederatedMediaRepository(context: Context) {
    private val appContext = context.applicationContext
    private val authRepository = AuthRepositoryProvider.getInstance(appContext)
    private val secureSessionStore = SecureSessionStore(appContext)
    private val networkPreferences = NetworkPreferences(appContext)

    fun availableServers(): List<FederatedServer> =
        authenticatedServers().map { server ->
            FederatedServer(
                id = server.id,
                name = server.displayName()
            )
        }

    suspend fun search(
        query: String,
        selectedTypes: Set<SearchMediaType>,
        limitPerServer: Int = 60
    ): FederatedMediaResponse = coroutineScope {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty() || selectedTypes.isEmpty()) {
            return@coroutineScope FederatedMediaResponse(emptyList(), emptyList())
        }

        val servers = authenticatedServers()
        val includeItemTypes = selectedTypes.joinToString(",") { type ->
            when (type) {
                SearchMediaType.MOVIE -> "Movie"
                SearchMediaType.SERIES -> "Series"
                SearchMediaType.EPISODE -> "Episode"
            }
        }
        // 手机端可能保存 4 台以上服务器；限制并发可避免瞬间占满连接池和图片请求队列。
        val semaphore = Semaphore(MAX_CONCURRENT_SERVERS)
        val outcomes = servers.map { server ->
            async {
                semaphore.withPermit {
                    searchServer(
                        server = server,
                        query = trimmedQuery,
                        includeItemTypes = includeItemTypes,
                        limit = limitPerServer
                    )
                }
            }
        }.awaitAll()

        FederatedMediaResponse(
            items = outcomes.flatMap { it.items },
            failures = outcomes.mapNotNull { it.failure }
        )
    }

    suspend fun loadContent(
        section: FederatedContentSection,
        excludedServerIds: Set<String> = emptySet()
    ): FederatedMediaResponse = coroutineScope {
        val servers = authenticatedServers().filter { server ->
            server.id !in excludedServerIds
        }
        val semaphore = Semaphore(MAX_CONCURRENT_SERVERS)
        val outcomes = servers.map { server ->
            async {
                semaphore.withPermit { loadServerContent(server, section) }
            }
        }.awaitAll()

        FederatedMediaResponse(
            items = outcomes.flatMap { it.items },
            failures = outcomes.mapNotNull { it.failure }
        )
    }

    private fun authenticatedServers(): List<AuthRepository.SavedServer> =
        authRepository.getActiveSessionSnapshot().savedServers
            .filter { server ->
                server.userId.isNotBlank() && secureSessionStore.hasToken(server.id)
            }
            .distinctBy { it.id }

    private suspend fun searchServer(
        server: AuthRepository.SavedServer,
        query: String,
        includeItemTypes: String,
        limit: Int
    ): ServerOutcome {
        val token = secureSessionStore.getToken(server.id)
            ?: return ServerOutcome.failure(server, "Missing access token")
        val serverType = runCatching { ServerType.valueOf(server.serverTypeRaw) }.getOrNull()
        // 搜索遵循该账户当前选中的线路，但不会写回或切换全局活动服务器。
        val baseUrl = server.activeLine()?.url ?: server.serverUrl

        return try {
            val api = createApi(baseUrl, token, serverType)
            var response = api.searchItems(
                userId = server.userId,
                searchTerm = query,
                includeItemTypes = includeItemTypes,
                recursive = true,
                limit = limit,
                fields = SEARCH_FIELDS
            )
            // 部分 Emby 版本对拉丁文本的 searchTerm 返回空，保留现有搜索使用的前缀兼容路径。
            if (
                query.any { it in 'A'..'Z' || it in 'a'..'z' } &&
                (!response.isSuccessful || response.body()?.items.isNullOrEmpty())
            ) {
                response = api.searchItemsByName(
                    userId = server.userId,
                    nameStartsWith = query,
                    includeItemTypes = includeItemTypes,
                    recursive = true,
                    limit = limit,
                    fields = SEARCH_FIELDS
                )
            }

            if (!response.isSuccessful || response.body() == null) {
                return ServerOutcome.failure(
                    server,
                    "HTTP ${response.code()} ${response.message()}".trim()
                )
            }

            val items = response.body()?.items.orEmpty()
                .filter { item -> !item.id.isNullOrBlank() }
                .distinctBy { item -> item.id }
                .map { item ->
                    FederatedMediaItem(
                        item = item,
                        serverId = server.id,
                        serverName = server.displayName(),
                        imageUrl = item.id?.let { itemId ->
                            buildImageUrl(baseUrl, itemId, token, "Primary")
                        }
                    )
                }
            ServerOutcome(items = items)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ServerOutcome.failure(
                server,
                error.message ?: error::class.java.simpleName
            )
        }
    }

    private suspend fun loadServerContent(
        server: AuthRepository.SavedServer,
        section: FederatedContentSection
    ): ServerOutcome {
        val token = secureSessionStore.getToken(server.id)
            ?: return ServerOutcome.failure(server, "Missing access token")
        val serverType = runCatching { ServerType.valueOf(server.serverTypeRaw) }.getOrNull()
        val baseUrl = server.activeLine()?.url ?: server.serverUrl

        return try {
            val api = createApi(baseUrl, token, serverType)
            val itemsResult = fetchServerContent(api, server.userId, section)
            val items = itemsResult.getOrElse { error ->
                return ServerOutcome.failure(
                    server,
                    error.message ?: error::class.java.simpleName
                )
            }
            ServerOutcome(
                items = items
                    .filter { item -> !item.id.isNullOrBlank() }
                    .distinctBy { item -> item.id }
                    .map { item ->
                        val (imageItemId, imageType) = imageSpec(item, section)
                        FederatedMediaItem(
                            item = item,
                            serverId = server.id,
                            serverName = server.displayName(),
                            imageUrl = imageItemId?.let { itemId ->
                                buildImageUrl(baseUrl, itemId, token, imageType)
                            }
                        )
                    }
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ServerOutcome.failure(server, error.message ?: error::class.java.simpleName)
        }
    }

    private suspend fun fetchServerContent(
        api: MediaServerApi,
        userId: String,
        section: FederatedContentSection
    ): Result<List<BaseItemDto>> {
        val response = when (section) {
            FederatedContentSection.CONTINUE_WATCHING -> api.getResumeItems(
                userId = userId,
                includeItemTypes = "Movie,Series,Episode",
                limit = CONTENT_LIMIT_PER_SERVER,
                recursive = true,
                sortBy = "DatePlayed",
                sortOrder = "Descending",
                fields = CONTENT_FIELDS
            )
            FederatedContentSection.FAVORITES -> api.getUserItems(
                userId = userId,
                includeItemTypes = "Movie,Series,Episode",
                recursive = true,
                sortBy = "DateCreated",
                sortOrder = "Descending",
                limit = CONTENT_LIMIT_PER_SERVER,
                filters = "IsFavorite",
                fields = CONTENT_FIELDS
            )
            FederatedContentSection.LIBRARIES -> api.getUserViews(userId)
        }
        if (!response.isSuccessful || response.body() == null) {
            return Result.failure(
                IllegalStateException("HTTP ${response.code()} ${response.message()}".trim())
            )
        }

        val items = response.body()?.items.orEmpty().let { loadedItems ->
            if (section == FederatedContentSection.LIBRARIES) {
                loadedItems.filter { item ->
                    item.collectionType !in setOf("boxsets", "playlists", "folders") &&
                        (item.type == "CollectionFolder" || item.type == "Folder")
                }
            } else {
                loadedItems
            }
        }
        return Result.success(items)
    }

    private fun createApi(
        baseUrl: String,
        token: String,
        serverType: ServerType?
    ): MediaServerApi = NetworkModule.createMediaServerApi(
        baseUrl = baseUrl,
        accessToken = token,
        serverType = serverType,
        // 多账户不能同时让独立 OkHttp Cache 占用同一目录；API 客户端本身仍按凭据缓存。
        storageDir = null,
        timeoutConfig = networkPreferences.getTimeoutConfig()
    )

    private fun imageSpec(
        item: BaseItemDto,
        section: FederatedContentSection
    ): Pair<String?, String> = when (section) {
        FederatedContentSection.CONTINUE_WATCHING -> when {
            !item.parentThumbItemId.isNullOrBlank() -> item.parentThumbItemId to "Thumb"
            item.imageTags?.containsKey("Thumb") == true -> item.id to "Thumb"
            !item.backdropImageTags.isNullOrEmpty() -> item.id to "Backdrop"
            !item.seriesId.isNullOrBlank() && !item.seriesThumbImageTag.isNullOrBlank() ->
                item.seriesId to "Thumb"
            else -> (item.parentPrimaryImageItemId ?: item.id) to "Primary"
        }
        FederatedContentSection.FAVORITES ->
            (if (item.type == "Episode") item.seriesId ?: item.id else item.id) to "Primary"
        FederatedContentSection.LIBRARIES -> item.id to "Primary"
    }

    private fun buildImageUrl(
        baseUrl: String,
        itemId: String,
        token: String,
        imageType: String
    ): String {
        val encodedToken = URLEncoder.encode(token, Charsets.UTF_8.name())
        return "${trimTrailingSlash(baseUrl)}/Items/$itemId/Images/$imageType" +
            "?maxWidth=640&quality=85&api_key=$encodedToken"
    }

    private data class ServerOutcome(
        val items: List<FederatedMediaItem> = emptyList(),
        val failure: FederatedServerFailure? = null
    ) {
        companion object {
            fun failure(server: AuthRepository.SavedServer, message: String) = ServerOutcome(
                failure = FederatedServerFailure(
                    serverId = server.id,
                    serverName = server.displayName(),
                    message = message
                )
            )
        }
    }

    private companion object {
        const val MAX_CONCURRENT_SERVERS = 3
        const val CONTENT_LIMIT_PER_SERVER = 30
        const val SEARCH_FIELDS =
            "ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId,Genres," +
                "CommunityRating,ProductionYear,Overview,IndexNumber,ParentIndexNumber"
        const val CONTENT_FIELDS =
            "ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId,Genres," +
                "CommunityRating,ProductionYear,Overview,IndexNumber,ParentIndexNumber," +
                "RunTimeTicks,UserData,ParentPrimaryImageItemId,CollectionType,ImageTags," +
                "BackdropImageTags,ParentThumbItemId,ParentThumbImageTag,SeriesThumbImageTag"
    }
}

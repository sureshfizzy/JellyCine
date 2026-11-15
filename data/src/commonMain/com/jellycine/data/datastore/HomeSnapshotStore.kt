package com.jellycine.data.datastore

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.HomeLibrarySectionData
import com.jellycine.data.model.PersistedHomeSnapshot
import com.jellycine.data.network.JellyCineJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.jellycine.data.util.currentTimeMillis
import com.jellycine.data.util.ioDispatcher
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

class HomeSnapshotStore(
    private val filesDir: String
) {
    private val fileSystem = FileSystem.SYSTEM
    private val homeSnapshotMutex = Mutex()
    private val homeSnapshotFileName = "JellyCineSnapshot.json"

    fun getPersistedHomeSnapshot(): PersistedHomeSnapshot? {
        return runCatching {
            readSnapshot(getHomeSnapshotFile())
        }.getOrNull()
    }

    suspend fun loadPersistedHomeSnapshot(
        expectedSnapshotKey: String,
        maxAgeMs: Long? = null
    ): PersistedHomeSnapshot? {
        return withContext(ioDispatcher) {
            homeSnapshotMutex.withLock {
                runCatching {
                    val parsed = readSnapshot(getHomeSnapshotFile()) ?: return@runCatching null
                    val isExpired = maxAgeMs?.let { ttl ->
                        currentTimeMillis() - parsed.updatedAt > ttl
                    } ?: false
                    val keyMismatch = parsed.snapshotKey != expectedSnapshotKey
                    if (isExpired || keyMismatch) null else parsed
                }.getOrNull()
            }
        }
    }

    suspend fun persistHomeSnapshot(
        snapshotKey: String,
        featuredHomeItems: List<BaseItemDto>? = null,
        continueWatchingItems: List<BaseItemDto>? = null,
        nextUpItems: List<BaseItemDto>? = null,
        homeLibrarySections: List<HomeLibrarySectionData>? = null,
        myMediaLibraries: List<BaseItemDto>? = null,
        username: String? = null,
        serverName: String? = null,
        serverUrl: String? = null,
        profileImageUrl: String? = null,
        isAdministrator: Boolean? = null,
        isVideoTranscodingAllowed: Boolean? = null,
        isAudioTranscodingAllowed: Boolean? = null,
        isSyncTranscodingAllowed: Boolean? = null,
        canManageSubtitles: Boolean? = null,
        subtitleLanguagePreference: String? = null
    ) {
        withContext(ioDispatcher) {
            homeSnapshotMutex.withLock {
                runCatching {
                    val file = getHomeSnapshotFile()
                    val existing = readSnapshot(file)
                    val sameSessionSnapshot = existing?.takeIf { it.snapshotKey == snapshotKey }
                    val next = PersistedHomeSnapshot(
                        snapshotKey = snapshotKey,
                        updatedAt = currentTimeMillis(),
                        featuredHomeItems = featuredHomeItems ?: sameSessionSnapshot?.featuredHomeItems.orEmpty(),
                        continueWatchingItems = continueWatchingItems ?: sameSessionSnapshot?.continueWatchingItems.orEmpty(),
                        nextUpItems = nextUpItems ?: sameSessionSnapshot?.nextUpItems.orEmpty(),
                        homeLibrarySections = homeLibrarySections ?: sameSessionSnapshot?.homeLibrarySections.orEmpty(),
                        myMediaLibraries = myMediaLibraries ?: sameSessionSnapshot?.myMediaLibraries.orEmpty(),
                        username = username ?: sameSessionSnapshot?.username,
                        serverName = serverName ?: sameSessionSnapshot?.serverName,
                        serverUrl = serverUrl ?: sameSessionSnapshot?.serverUrl,
                        profileImageUrl = profileImageUrl ?: sameSessionSnapshot?.profileImageUrl,
                        isAdministrator = isAdministrator ?: sameSessionSnapshot?.isAdministrator,
                        isVideoTranscodingAllowed = isVideoTranscodingAllowed
                            ?: sameSessionSnapshot?.isVideoTranscodingAllowed,
                        isAudioTranscodingAllowed = isAudioTranscodingAllowed
                            ?: sameSessionSnapshot?.isAudioTranscodingAllowed,
                        isSyncTranscodingAllowed = isSyncTranscodingAllowed
                            ?: sameSessionSnapshot?.isSyncTranscodingAllowed,
                        canManageSubtitles = canManageSubtitles
                            ?: sameSessionSnapshot?.canManageSubtitles,
                        subtitleLanguagePreference = subtitleLanguagePreference
                            ?: sameSessionSnapshot?.subtitleLanguagePreference
                    )
                    writeSnapshotAtomically(file, next)
                }
            }
        }
    }

    suspend fun clearPersistedHomeSnapshot() {
        withContext(ioDispatcher) {
            homeSnapshotMutex.withLock {
                runCatching {
                    val file = getHomeSnapshotFile()
                    if (fileSystem.exists(file)) {
                        fileSystem.delete(file)
                    }
                }
            }
        }
    }

    private fun getHomeSnapshotFile(): Path = filesDir.toPath() / homeSnapshotFileName

    private fun readSnapshot(file: Path): PersistedHomeSnapshot? {
        if (!fileSystem.exists(file)) return null
        return runCatching {
            val rawJson = fileSystem.read(file) { readUtf8() }
            JellyCineJson.decodeFromString<PersistedHomeSnapshot>(rawJson)
        }.getOrElse {
            fileSystem.delete(file)
            null
        }
    }

    private fun writeSnapshotAtomically(
        file: Path,
        snapshot: PersistedHomeSnapshot
    ) {
        val temp = filesDir.toPath() / "$homeSnapshotFileName.tmp"
        try {
            fileSystem.write(temp) { writeUtf8(JellyCineJson.encodeToString(snapshot)) }
            fileSystem.atomicMove(temp, file)
        } catch (error: Exception) {
            fileSystem.delete(temp)
            throw error
        }
    }
}

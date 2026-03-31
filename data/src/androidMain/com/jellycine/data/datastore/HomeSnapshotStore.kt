package com.jellycine.data.datastore

import android.util.AtomicFile
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.HomeLibrarySectionData
import com.jellycine.data.model.PersistedHomeSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets

class HomeSnapshotStore(
    private val filesDir: File,
    private val gson: Gson = Gson()
) {
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
        return withContext(Dispatchers.IO) {
            homeSnapshotMutex.withLock {
                runCatching {
                    val parsed = readSnapshot(getHomeSnapshotFile()) ?: return@runCatching null
                    val isExpired = maxAgeMs?.let { ttl ->
                        System.currentTimeMillis() - parsed.updatedAt > ttl
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
        isAudioTranscodingAllowed: Boolean? = null
    ) {
        withContext(Dispatchers.IO) {
            homeSnapshotMutex.withLock {
                runCatching {
                    val file = getHomeSnapshotFile()
                    val existing = readSnapshot(file)
                    val sameSessionSnapshot = existing?.takeIf { it.snapshotKey == snapshotKey }
                    val next = PersistedHomeSnapshot(
                        snapshotKey = snapshotKey,
                        updatedAt = System.currentTimeMillis(),
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
                            ?: sameSessionSnapshot?.isAudioTranscodingAllowed
                    )
                    writeSnapshotAtomically(file, next)
                }
            }
        }
    }

    suspend fun clearPersistedHomeSnapshot() {
        withContext(Dispatchers.IO) {
            homeSnapshotMutex.withLock {
                runCatching {
                    val file = getHomeSnapshotFile()
                    if (file.exists()) {
                        file.delete()
                    }
                }
            }
        }
    }

    private fun getHomeSnapshotFile(): File = filesDir.resolve(homeSnapshotFileName)

    private fun readSnapshot(file: File): PersistedHomeSnapshot? {
        if (!file.exists()) return null
        return runCatching {
            val rawJson = file.readText()
            val jsonElement = JsonParser.parseString(rawJson)
            gson.fromJson(jsonElement, PersistedHomeSnapshot::class.java)
        }.getOrElse {
            file.delete()
            null
        }
    }

    private fun writeSnapshotAtomically(
        file: File,
        snapshot: PersistedHomeSnapshot
    ) {
        val atomicFile = AtomicFile(file)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(gson.toJson(snapshot).toByteArray(StandardCharsets.UTF_8))
            stream.flush()
            atomicFile.finishWrite(stream)
        } catch (error: Exception) {
            stream?.let { atomicFile.failWrite(it) }
            throw error
        }
    }
}
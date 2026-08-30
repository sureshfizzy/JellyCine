package com.vela.data.update

import android.content.Context
import android.os.Build
import com.vela.data.preferences.NetworkPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class AppUpdateCheckException(message: String) : IOException(message)

class AppUpdateRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = AppUpdatePreferences(appContext)
    private val networkPreferences = NetworkPreferences(appContext)

    fun currentFlavor(): AppFlavor = AppFlavor.fromPackageName(appContext.packageName)

    fun deviceAbis(): List<String> = Build.SUPPORTED_ABIS.toList()

    fun currentMirror(): DownloadMirror = preferences.getMirror()

    fun customPrefix(): String = preferences.getCustomPrefix()

    fun setMirrorId(id: String) {
        preferences.setMirrorId(id)
    }

    fun setCustomPrefix(prefix: String) {
        preferences.setCustomPrefix(prefix)
    }

    suspend fun checkLatest(): AppUpdateRelease = withContext(Dispatchers.IO) {
        val mirror = preferences.getMirror()
        val url = accelerateGithubUrl(GITHUB_LATEST_RELEASE_URL, mirror.prefix)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        apiClient().newCall(request).execute().use { response ->
            val body = response.body.string()
            if (response.code == 404) {
                throw AppUpdateCheckException("NO_RELEASE")
            }
            if (!response.isSuccessful) {
                throw AppUpdateCheckException("HTTP ${response.code}")
            }
            parseGithubReleaseJson(body)
        }
    }

    suspend fun download(
        asset: AppUpdateAsset,
        onProgress: (received: Long, total: Long?) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val mirror = preferences.getMirror()
        val url = accelerateGithubUrl(asset.downloadUrl, mirror.prefix)
        val target = File(updatesDir(), asset.name)
        if (target.exists()) {
            target.delete()
        }
        val temp = File(target.parentFile, "${target.name}.part")
        if (temp.exists()) {
            temp.delete()
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        downloadClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw AppUpdateCheckException("HTTP ${response.code}")
            }
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 }
            body.byteStream().use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var received = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        received += read
                        onProgress(received, total)
                    }
                    output.flush()
                }
            }
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        target
    }

    fun updatesDir(): File {
        val dir = File(appContext.cacheDir, "updates")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun apiClient(): OkHttpClient {
        val timeouts = networkPreferences.getTimeoutConfig()
        return OkHttpClient.Builder()
            .callTimeout(timeouts.requestTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .connectTimeout(timeouts.connectionTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeouts.socketTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(timeouts.socketTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun downloadClient(): OkHttpClient {
        val timeouts = networkPreferences.getTimeoutConfig()
        return OkHttpClient.Builder()
            .connectTimeout(timeouts.connectionTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(timeouts.socketTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    companion object {
        private const val USER_AGENT = "Vela-AppUpdate"
        private const val DEFAULT_BUFFER_SIZE = 64 * 1024
    }
}

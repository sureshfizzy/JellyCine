package com.vela.player.discord

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

class DiscordRpcManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "DiscordRpcManager"
        const val APPLICATION_ID = "1271740419083862057"
        const val REDIRECT_URI = "https://jellycine.org/discord/callback/index.html"
        private const val OAUTH2_AUTHORIZE_URL = "https://discord.com/oauth2/authorize"
        private const val OAUTH2_TOKEN_URL = "https://discord.com/api/oauth2/token"
        private const val DISCORD_USER_URL = "https://discord.com/api/users/@me"
        private const val DISCORD_SDK_INIT_CLASS = "com.discord.socialsdk.DiscordSocialSdkInit"
        private const val DISCORD_SDK_INIT_METHOD = "setEngineActivity"
        private const val SCOPES = "identify sdk.social_layer_presence"
        private const val PREFS_NAME = "discord_auth_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USERNAME = "discord_username"
        private const val KEY_AVATAR_URL = "discord_avatar_url"

        @Volatile
        private var instance: DiscordRpcManager? = null

        fun getInstance(context: Context): DiscordRpcManager {
            return instance ?: synchronized(this) {
                instance ?: DiscordRpcManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var isInitialized = false
    private var codeVerifier: String? = null
    private var activityRef: Activity? = null

    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun setActivity(activity: Activity) {
        activityRef = activity
    }

    fun initialize() {
        if (isInitialized) return
        if (!isDiscordInstalled()) return
        val accessToken = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
        if (accessToken.isNullOrBlank()) return
        try {
            val activity = activityRef ?: return
            initializeDiscordSdk(activity)
            System.loadLibrary("discord_bridge")
            isInitialized = nativeInitialize(APPLICATION_ID, accessToken)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load discord_bridge native library", e)
        } catch (e: Exception) {
            Log.e(TAG, "Discord SDK initialization error", e)
        }
    }

    private fun initializeDiscordSdk(activity: Activity) {
        // Discord AAR 是可选的发布依赖；反射避免无 SDK 的 CI 构建在编译期解析其类。
        val sdkInitClass = Class.forName(DISCORD_SDK_INIT_CLASS)
        sdkInitClass.getMethod(DISCORD_SDK_INIT_METHOD, Activity::class.java).invoke(null, activity)
    }

    fun isDiscordInstalled(): Boolean {
        val packages = listOf("com.discord", "com.discord.rlz")
        return packages.any { pkg ->
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    fun isAuthorized(): Boolean {
        return !encryptedPrefs.getString(KEY_ACCESS_TOKEN, null).isNullOrBlank()
    }

    fun getStoredUsername(): String? {
        return encryptedPrefs.getString(KEY_USERNAME, null)
    }

    fun getAuthorizationUrl(): String {
        codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier!!)

        return Uri.parse(OAUTH2_AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("client_id", APPLICATION_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
            .toString()
    }

    suspend fun exchangeCodeForToken(code: String): Boolean {
        val verifier = codeVerifier ?: return false

        return withContext(Dispatchers.IO) {
            try {
                val params = listOf(
                    "client_id=$APPLICATION_ID",
                    "grant_type=authorization_code",
                    "code=$code",
                    "redirect_uri=${Uri.encode(REDIRECT_URI)}",
                    "code_verifier=$verifier"
                ).joinToString("&")

                val url = URL(OAUTH2_TOKEN_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.doOutput = true
                conn.outputStream.use { it.write(params.toByteArray()) }

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(body)
                    val accessToken = json.getString("access_token")
                    val refreshToken = json.optString("refresh_token", "")

                    encryptedPrefs.edit()
                        .putString(KEY_ACCESS_TOKEN, accessToken)
                        .putString(KEY_REFRESH_TOKEN, refreshToken)
                        .apply()

                    fetchAndSaveUserInfo(accessToken)
                    codeVerifier = null
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token exchange failed", e)
                false
            }
        }
    }

    private fun fetchAndSaveUserInfo(accessToken: String) {
        try {
            val url = URL(DISCORD_USER_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            if (conn.responseCode == 200) {
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val username = json.optString("global_name", json.getString("username"))
                val id = json.getString("id")
                val avatar = json.optString("avatar", "")
                val avatarUrl = if (avatar.isNotBlank()) {
                    "https://cdn.discordapp.com/avatars/$id/$avatar.png"
                } else ""

                encryptedPrefs.edit()
                    .putString(KEY_USERNAME, username)
                    .putString(KEY_AVATAR_URL, avatarUrl)
                    .apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch user info", e)
        }
    }

    fun disconnect() {
        clearPresence()
        shutdown()
        encryptedPrefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USERNAME)
            .remove(KEY_AVATAR_URL)
            .apply()
    }

    fun updatePresence(info: NowPlayingInfo) {
        if (!isInitialized) initialize()
        if (!isInitialized) return
        try {
            nativeUpdatePresence(
                details = info.discordDetails,
                state = info.discordState.orEmpty(),
                largeImageKey = info.imageUrl.orEmpty(),
                largeImageText = info.title,
                startTimestamp = info.startTimestampMs,
                activityType = activityTypeFor(info.mediaType)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update Discord presence", e)
        }
    }

    fun clearPresence() {
        if (!isInitialized) return
        try {
            nativeClearPresence()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear Discord presence", e)
        }
    }

    fun shutdown() {
        if (!isInitialized) return
        try {
            nativeShutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to shutdown Discord SDK", e)
        }
        isInitialized = false
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun activityTypeFor(mediaType: NowPlayingInfo.MediaType): Int {
        return when (mediaType) {
            NowPlayingInfo.MediaType.MUSIC -> 2
            else -> 3
        }
    }

    private external fun nativeInitialize(applicationId: String, accessToken: String): Boolean
    private external fun nativeUpdatePresence(
        details: String,
        state: String,
        largeImageKey: String,
        largeImageText: String,
        startTimestamp: Long,
        activityType: Int
    )
    private external fun nativeClearPresence()
    private external fun nativeShutdown()
}

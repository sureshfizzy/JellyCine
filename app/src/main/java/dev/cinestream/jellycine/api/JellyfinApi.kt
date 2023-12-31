package dev.cinestream.jellycine.api

import android.content.Context
import dev.cinestream.jellycine.BuildConfig
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.android
import org.jellyfin.sdk.api.operations.*
import org.jellyfin.sdk.model.ClientInfo
import java.util.*

class JellyfinApi(context: Context, baseUrl: String) {
    val jellyfin = Jellyfin {
        clientInfo =
            ClientInfo(name = BuildConfig.APPLICATION_ID, version = BuildConfig.VERSION_NAME)
        android(context)
    }
    val api = jellyfin.createApi(baseUrl = baseUrl)
    val systemApi = SystemApi(api)
    val userApi = UserApi(api)
    var userId: UUID? = null
    val viewsApi = UserViewsApi(api)
    val itemsApi = ItemsApi(api)
    val userLibraryApi = UserLibraryApi(api)

    companion object {
        @Volatile
        private var INSTANCE: JellyfinApi? = null

        fun getInstance(context: Context, baseUrl: String): JellyfinApi {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = JellyfinApi(context.applicationContext, baseUrl)
                    INSTANCE = instance
                }
                return instance
            }
        }

        fun newInstance(context: Context, baseUrl: String): JellyfinApi {
            synchronized(this) {
                val instance = JellyfinApi(context.applicationContext, baseUrl)
                INSTANCE = instance
                return instance
            }
        }
    }
}
package com.vela.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vela.data.datastore.DataStoreProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 聚合视界只保存服务器展示偏好；服务器身份和凭据仍由 AuthRepository 与 SecureSessionStore 管理。
 */
class FederatedViewPreferences(context: Context) {
    private val dataStore = DataStoreProvider.getDataStore(context.applicationContext)

    val excludedServerIds: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[EXCLUDED_SERVERS].orEmpty()
    }

    suspend fun setServerIncluded(serverId: String, included: Boolean) {
        if (serverId.isBlank()) return
        dataStore.edit { preferences ->
            val excludedIds = preferences[EXCLUDED_SERVERS].orEmpty()
            preferences[EXCLUDED_SERVERS] = if (included) {
                excludedIds - serverId
            } else {
                excludedIds + serverId
            }
        }
    }

    private companion object {
        val EXCLUDED_SERVERS =
            stringSetPreferencesKey("federated_continue_excluded_servers_v1")
    }
}

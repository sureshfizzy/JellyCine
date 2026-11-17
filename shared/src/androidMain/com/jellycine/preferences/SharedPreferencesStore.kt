package com.jellycine.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

fun Preferences(context: Context): Preferences {
    return Preferences(SharedPreferencesStore(context))
}

class SharedPreferencesStore(
    context: Context,
    name: String = Preferences.PREFS_NAME
) : KeyValueStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(name, Context.MODE_PRIVATE)

    override val changes: Flow<String?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> trySend(key) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    override fun getInt(key: String, default: Int) = prefs.getInt(key, default)
    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()

    override fun getLong(key: String, default: Long) = prefs.getLong(key, default)
    override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()

    override fun getFloat(key: String, default: Float) = prefs.getFloat(key, default)
    override fun putFloat(key: String, value: Float) = prefs.edit().putFloat(key, value).apply()

    override fun getString(key: String, default: String?): String? = prefs.getString(key, default)
    override fun putString(key: String, value: String?) = prefs.edit().putString(key, value).apply()

    override fun getStringSet(key: String, default: Set<String>): Set<String> =
        prefs.getStringSet(key, default).orEmpty()

    override fun putStringSet(key: String, value: Set<String>) = prefs.edit().putStringSet(key, value).apply()

    override fun contains(key: String) = prefs.contains(key)
    override fun remove(key: String) = prefs.edit().remove(key).apply()
    override fun keys(): Set<String> = prefs.all.keys.toSet()
}

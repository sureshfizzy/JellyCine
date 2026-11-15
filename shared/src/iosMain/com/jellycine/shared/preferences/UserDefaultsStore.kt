package com.jellycine.shared.preferences

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDefaultsDidChangeNotification

class UserDefaultsStore : KeyValueStore {
    private val defaults = NSUserDefaults(suiteName = Preferences.PREFS_NAME)

    override val changes: Flow<String?> = callbackFlow {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = NSUserDefaultsDidChangeNotification,
            `object` = defaults,
            queue = NSOperationQueue.mainQueue
        ) { trySend(null) }
        awaitClose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }

    private fun has(key: String): Boolean = defaults.objectForKey(key) != null

    override fun getBoolean(key: String, default: Boolean) =
        if (has(key)) defaults.boolForKey(key) else default

    override fun putBoolean(key: String, value: Boolean) = defaults.setBool(value, key)

    override fun getInt(key: String, default: Int) =
        if (has(key)) defaults.integerForKey(key).toInt() else default

    override fun putInt(key: String, value: Int) = defaults.setInteger(value.toLong(), key)

    override fun getFloat(key: String, default: Float) =
        if (has(key)) defaults.floatForKey(key) else default

    override fun putFloat(key: String, value: Float) = defaults.setFloat(value, key)

    override fun getString(key: String, default: String?): String? =
        defaults.stringForKey(key) ?: default

    override fun putString(key: String, value: String?) = defaults.setObject(value, key)

    override fun getStringSet(key: String, default: Set<String>): Set<String> =
        (defaults.stringArrayForKey(key) as? List<*>)?.filterIsInstance<String>()?.toSet() ?: default

    override fun putStringSet(key: String, value: Set<String>) = defaults.setObject(value.toList(), key)
}

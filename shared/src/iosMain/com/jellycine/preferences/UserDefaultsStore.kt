package com.jellycine.preferences

import kotlinx.coroutines.flow.*
import platform.Foundation.NSUserDefaults

class UserDefaultsStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults
) : KeyValueStore {
    private val _changes = MutableSharedFlow<String?>(extraBufferCapacity = 64)
    override val changes: Flow<String?> = _changes.asSharedFlow()

    private fun has(key: String): Boolean = defaults.objectForKey(key) != null

    override fun getBoolean(key: String, default: Boolean): Boolean =
        if (has(key)) defaults.boolForKey(key) else default

    override fun putBoolean(key: String, value: Boolean) {
        defaults.setBool(value, key)
        _changes.tryEmit(key)
    }

    override fun getInt(key: String, default: Int): Int =
        if (has(key)) defaults.integerForKey(key).toInt() else default

    override fun putInt(key: String, value: Int) {
        defaults.setInteger(value.toLong(), key)
        _changes.tryEmit(key)
    }

    override fun getLong(key: String, default: Long): Long =
        if (has(key)) defaults.integerForKey(key) else default

    override fun putLong(key: String, value: Long) {
        defaults.setInteger(value, key)
        _changes.tryEmit(key)
    }

    override fun getFloat(key: String, default: Float): Float =
        if (has(key)) defaults.floatForKey(key) else default

    override fun putFloat(key: String, value: Float) {
        defaults.setFloat(value, key)
        _changes.tryEmit(key)
    }

    override fun getString(key: String, default: String?): String? =
        defaults.stringForKey(key) ?: default

    override fun putString(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, key)
        _changes.tryEmit(key)
    }

    override fun getStringSet(key: String, default: Set<String>): Set<String> =
        defaults.stringArrayForKey(key)?.filterIsInstance<String>()?.toSet() ?: default

    override fun putStringSet(key: String, value: Set<String>) {
        defaults.setObject(value.toList(), key)
        _changes.tryEmit(key)
    }

    override fun contains(key: String): Boolean = has(key)

    override fun remove(key: String) {
        defaults.removeObjectForKey(key)
        _changes.tryEmit(key)
    }

    override fun keys(): Set<String> =
        defaults.dictionaryRepresentation().keys.filterIsInstance<String>().toSet()
}

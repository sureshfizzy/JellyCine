package com.jellycine.shared.preferences

import kotlinx.coroutines.flow.Flow

interface KeyValueStore {
    val changes: Flow<String?>

    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)

    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)

    fun getFloat(key: String, default: Float): Float
    fun putFloat(key: String, value: Float)

    fun getString(key: String, default: String?): String?
    fun putString(key: String, value: String?)

    fun getStringSet(key: String, default: Set<String>): Set<String>
    fun putStringSet(key: String, value: Set<String>)
}

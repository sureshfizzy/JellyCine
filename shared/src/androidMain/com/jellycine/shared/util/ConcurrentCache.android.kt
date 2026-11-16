package com.jellycine.shared.util

import java.util.concurrent.ConcurrentHashMap

actual class ConcurrentCache<K : Any, V : Any> actual constructor() {
    private val map = ConcurrentHashMap<K, V>()

    actual operator fun get(key: K): V? = map[key]

    actual operator fun set(key: K, value: V) {
        map[key] = value
    }

    actual fun putIfAbsent(key: K, value: V): V? = map.putIfAbsent(key, value)

    actual fun remove(key: K) {
        map.remove(key)
    }
}

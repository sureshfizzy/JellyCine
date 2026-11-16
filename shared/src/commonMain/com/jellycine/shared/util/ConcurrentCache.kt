package com.jellycine.shared.util

expect class ConcurrentCache<K : Any, V : Any>() {
    operator fun get(key: K): V?
    operator fun set(key: K, value: V)
    fun putIfAbsent(key: K, value: V): V?
    fun remove(key: K)
}

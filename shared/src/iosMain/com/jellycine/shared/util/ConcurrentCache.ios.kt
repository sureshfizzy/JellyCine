package com.jellycine.shared.util

import kotlin.concurrent.AtomicReference

actual class ConcurrentCache<K : Any, V : Any> actual constructor() {
    private val ref = AtomicReference<Map<K, V>>(emptyMap())

    actual operator fun get(key: K): V? = ref.value[key]

    actual operator fun set(key: K, value: V) {
        while (true) {
            val current = ref.value
            if (ref.compareAndSet(current, current + (key to value))) return
        }
    }

    actual fun putIfAbsent(key: K, value: V): V? {
        while (true) {
            val current = ref.value
            current[key]?.let { return it }
            if (ref.compareAndSet(current, current + (key to value))) return null
        }
    }

    actual fun remove(key: K) {
        while (true) {
            val current = ref.value
            if (key !in current) return
            if (ref.compareAndSet(current, current - key)) return
        }
    }
}

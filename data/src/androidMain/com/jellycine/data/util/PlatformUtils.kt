package com.jellycine.data.util

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
actual val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO

actual val platformName: String = "Android"

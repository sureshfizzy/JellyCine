package com.jellycine.data.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*

@OptIn(ExperimentalForeignApi::class)
actual fun currentTimeMillis(): Long {
    val now = NSDate()
    return (now.timeIntervalSince1970 * 1000).toLong()
}
actual val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default

actual val platformName: String = "iOS"

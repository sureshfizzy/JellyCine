package com.vela.data.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

@OptIn(ExperimentalForeignApi::class)
actual fun currentTimeMillis(): Long {
    val now = NSDate()
    return (now.timeIntervalSince1970 * 1000).toLong()
}
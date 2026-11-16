package com.jellycine.shared.util

import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.allowRgb565

actual fun ImageRequest.Builder.platformBitmapConfig(allowRgb565: Boolean): ImageRequest.Builder {
    return allowHardware(true).allowRgb565(allowRgb565)
}

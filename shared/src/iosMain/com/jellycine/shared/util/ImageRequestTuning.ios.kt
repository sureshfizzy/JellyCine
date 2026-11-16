package com.jellycine.shared.util

import coil3.request.ImageRequest

actual fun ImageRequest.Builder.platformBitmapConfig(allowRgb565: Boolean): ImageRequest.Builder = this

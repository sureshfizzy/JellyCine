package com.jellycine.shared.util

import coil3.request.ImageRequest

expect fun ImageRequest.Builder.platformBitmapConfig(allowRgb565: Boolean): ImageRequest.Builder

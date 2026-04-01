package com.jellycine.shared.util.image

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.BaseItemPerson

private fun String?.nonBlankTag(): String? = this?.takeIf { it.isNotBlank() }

private fun List<String>?.tagAt(imageIndex: Int): String? {
    return this?.getOrNull(imageIndex)?.nonBlankTag()
}

private fun Map<String, String>?.tagFor(imageType: String): String? {
    return this
        ?.entries
        ?.firstOrNull { (type, _) -> type.equals(imageType, ignoreCase = true) }
        ?.value
        .nonBlankTag()
}

fun BaseItemDto.imageTagFor(
    imageType: String,
    targetItemId: String? = id,
    imageIndex: Int = 0
): String? {
    val directTag = imageTags.tagFor(imageType)

    return when {
        imageType.equals("Primary", ignoreCase = true) -> when {
            !targetItemId.isNullOrBlank() && targetItemId == seriesId -> {
                seriesPrimaryImageTag.nonBlankTag()
                    ?: parentPrimaryImageTag.nonBlankTag()
                    ?: directTag
            }

            !targetItemId.isNullOrBlank() && targetItemId == parentPrimaryImageItemId -> {
                parentPrimaryImageTag.nonBlankTag() ?: directTag
            }

            else -> {
                directTag
                    ?: parentPrimaryImageTag.nonBlankTag()
                    ?: seriesPrimaryImageTag.nonBlankTag()
                    ?: albumPrimaryImageTag.nonBlankTag()
                    ?: channelPrimaryImageTag.nonBlankTag()
            }
        }

        imageType.equals("Backdrop", ignoreCase = true) -> when {
            !targetItemId.isNullOrBlank() &&
                (targetItemId == seriesId || targetItemId == parentBackdropItemId) -> {
                parentBackdropImageTags.tagAt(imageIndex)
                    ?: backdropImageTags.tagAt(imageIndex)
                    ?: directTag
            }

            else -> {
                backdropImageTags.tagAt(imageIndex)
                    ?: directTag
                    ?: parentBackdropImageTags.tagAt(imageIndex)
            }
        }

        imageType.equals("Thumb", ignoreCase = true) -> when {
            !targetItemId.isNullOrBlank() &&
                (targetItemId == seriesId || targetItemId == parentThumbItemId) -> {
                seriesThumbImageTag.nonBlankTag()
                    ?: parentThumbImageTag.nonBlankTag()
                    ?: directTag
            }

            else -> {
                directTag
                    ?: parentThumbImageTag.nonBlankTag()
                    ?: seriesThumbImageTag.nonBlankTag()
            }
        }

        imageType.equals("Logo", ignoreCase = true) -> {
            directTag ?: parentLogoImageTag.nonBlankTag()
        }

        else -> directTag
    }
}

fun BaseItemPerson.primaryImageTagOrNull(): String? = primaryImageTag.nonBlankTag()

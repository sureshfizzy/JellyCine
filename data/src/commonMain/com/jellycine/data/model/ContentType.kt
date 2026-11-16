package com.jellycine.data.model

enum class ContentType {
    ALL, MOVIES, SERIES, EPISODES, MOVIES_GENRE, TVSHOWS_GENRE, SEERR_STUDIO, SEERR_NETWORK, AWARD
}

fun ContentType.isSeerrCatalog(): Boolean =
    this == ContentType.SEERR_STUDIO || this == ContentType.SEERR_NETWORK

fun ContentType.isLibraryCatalog(): Boolean =
    this == ContentType.ALL ||
        this == ContentType.MOVIES ||
        this == ContentType.SERIES ||
        this == ContentType.EPISODES

fun ContentType.isGenreCatalog(): Boolean =
    this == ContentType.MOVIES_GENRE ||
        this == ContentType.TVSHOWS_GENRE

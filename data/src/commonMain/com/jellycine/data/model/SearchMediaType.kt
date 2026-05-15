package com.jellycine.data.model

enum class SearchMediaType(val serverValue: String) {
    MOVIE("Movie"),
    SERIES("Series"),
    EPISODE("Episode")
}

fun Set<SearchMediaType>.toSearchQueries(): List<Pair<String, Long>> =
    buildList {
        this@toSearchQueries.filter { it != SearchMediaType.EPISODE }
            .joinToString(",") { it.serverValue }
            .takeIf { it.isNotBlank() }
            ?.let { add(it to 30_000L) }
        if (SearchMediaType.EPISODE in this@toSearchQueries) {
            add(SearchMediaType.EPISODE.serverValue to 12_000L)
        }
    }
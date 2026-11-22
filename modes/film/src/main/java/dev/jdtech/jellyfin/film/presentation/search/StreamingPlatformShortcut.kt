package dev.jdtech.jellyfin.film.presentation.search

data class StreamingPlatformShortcut(
    val id: String,
    val label: String,
    val shortBadge: String,
    val searchQuery: String,
    val brandColorHex: Long,
)

val DEFAULT_STREAMING_PLATFORM_SHORTCUTS =
    listOf(
        StreamingPlatformShortcut(
            id = "netflix",
            label = "Netflix",
            shortBadge = "N",
            searchQuery = "Netflix",
            brandColorHex = 0xFFE50914,
        ),
        StreamingPlatformShortcut(
            id = "prime",
            label = "Prime Video",
            shortBadge = "P",
            searchQuery = "Prime Video",
            brandColorHex = 0xFF00A8E1,
        ),
        StreamingPlatformShortcut(
            id = "hbo",
            label = "HBO / Max",
            shortBadge = "HBO",
            searchQuery = "HBO",
            brandColorHex = 0xFF5822B4,
        ),
        StreamingPlatformShortcut(
            id = "disney",
            label = "Disney+",
            shortBadge = "D+",
            searchQuery = "Disney+",
            brandColorHex = 0xFF113CCF,
        ),
        StreamingPlatformShortcut(
            id = "paramount",
            label = "Paramount+",
            shortBadge = "P+",
            searchQuery = "Paramount+",
            brandColorHex = 0xFF0064FF,
        ),
        StreamingPlatformShortcut(
            id = "apple",
            label = "Apple TV+",
            shortBadge = "TV+",
            searchQuery = "Apple TV+",
            brandColorHex = 0xFF2C2C2E,
        ),
    )


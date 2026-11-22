package dev.jdtech.jellyfin.core.presentation.dummy

import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.JellyxCollection
import dev.jdtech.jellyfin.models.JellyxImages
import java.util.UUID

private val dummyMoviesCollection =
    JellyxCollection(
        id = UUID.randomUUID(),
        name = "Movies",
        type = CollectionType.Movies,
        images = JellyxImages(),
    )

private val dummyShowsCollection =
    JellyxCollection(
        id = UUID.randomUUID(),
        name = "Shows",
        type = CollectionType.TvShows,
        images = JellyxImages(),
    )

val dummyCollections = listOf(dummyMoviesCollection, dummyShowsCollection)

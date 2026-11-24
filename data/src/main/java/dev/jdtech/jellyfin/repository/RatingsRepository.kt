package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.models.JellyxItem
import dev.jdtech.jellyfin.models.JellyxRatings
import java.util.UUID

interface RatingsRepository {
    fun extractNativeRatings(communityRating: Float?, criticRating: Float?): JellyxRatings

    fun extractNativeRatings(item: JellyxItem): JellyxRatings

    suspend fun getEnrichedRatings(
        itemId: UUID,
        communityRating: Float?,
        criticRating: Float?,
        providerIds: Map<String, String>,
        isSeries: Boolean = false,
    ): JellyxRatings
}

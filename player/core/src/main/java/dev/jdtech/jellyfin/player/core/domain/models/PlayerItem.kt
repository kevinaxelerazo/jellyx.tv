package dev.jdtech.jellyfin.player.core.domain.models

import android.os.Parcelable
import java.util.UUID
import kotlinx.parcelize.Parcelize

@Parcelize
data class PlayerItem(
    val name: String,
    val itemId: UUID,
    val mediaSourceId: String,
    val playbackPosition: Long,
    val mediaSourceUri: String = "",
    val parentIndexNumber: Int? = null,
    val indexNumber: Int? = null,
    val indexNumberEnd: Int? = null,
    val externalSubtitles: List<ExternalSubtitle> = emptyList(),
    val chapters: List<PlayerChapter> = emptyList(),
    val trickplayInfo: TrickplayInfo? = null,
    val isLiveTv: Boolean = false,
    val sourceName: String = "",
    val sourceBitrate: Int? = null,
    val videoRangeLabel: String? = null,
    val videoStreamBitrate: Int? = null,
    val audioStreamBitrate: Int? = null,
    val totalSourcesCount: Int = 1,
    val liveStreamId: String? = null,
) : Parcelable

package dev.jdtech.jellyfin.film.domain

import dev.jdtech.jellyfin.models.AudioChannel
import dev.jdtech.jellyfin.models.AudioCodec
import dev.jdtech.jellyfin.models.DisplayProfile
import dev.jdtech.jellyfin.models.JellyxSource
import dev.jdtech.jellyfin.models.Resolution
import dev.jdtech.jellyfin.models.VideoCodec
import dev.jdtech.jellyfin.models.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRangeType

object VideoMetadataParser {
    suspend fun parse(sources: List<JellyxSource>): VideoMetadata {
        if (sources.isEmpty()) {
            return VideoMetadata(
                size = 0L,
                videoTracks = emptyList(),
                audioTracks = emptyList(),
                subtitleTracks = emptyList(),
                resolution = emptyList(),
                videoCodecs = emptyList(),
                displayProfiles = emptyList(),
                audioChannels = emptyList(),
                audioCodecs = emptyList(),
                isAtmos = emptyList(),
            )
        }
        val parsed = sources.map { parse(it) }
        return VideoMetadata(
            size = parsed.first().size,
            videoTracks = parsed.flatMap { it.videoTracks }.distinct(),
            audioTracks = parsed.flatMap { it.audioTracks }.distinct(),
            subtitleTracks = parsed.flatMap { it.subtitleTracks }.distinct(),
            resolution = parsed.flatMap { it.resolution }.distinct(),
            videoCodecs = parsed.flatMap { it.videoCodecs }.distinct(),
            displayProfiles = parsed.flatMap { it.displayProfiles }.distinct(),
            audioChannels = parsed.flatMap { it.audioChannels }.distinct(),
            audioCodecs = parsed.flatMap { it.audioCodecs }.distinct(),
            isAtmos = parsed.flatMap { it.isAtmos },
        )
    }

    suspend fun parse(source: JellyxSource): VideoMetadata {
        val videoTracks = mutableListOf<String>()
        val audioTracks = mutableListOf<String>()
        val subtitleTracks = mutableListOf<String>()
        val resolution = mutableListOf<Resolution>()
        val videoCodecs = mutableListOf<VideoCodec?>()
        val audioChannels = mutableListOf<AudioChannel>()
        val displayProfiles = mutableListOf<DisplayProfile>()
        val audioCodecs = mutableListOf<AudioCodec?>()
        val isAtmosAudio = mutableListOf<Boolean>()

        withContext(Dispatchers.Default) {
            source.mediaStreams.forEach { stream ->
                when (stream.type) {
                    MediaStreamType.AUDIO -> {
                        stream.displayTitle?.let { audioTracks.add(it) }

                        /**
                         * Match audio profile from
                         * [org.jellyfin.sdk.model.api.MediaStream.channelLayout]
                         */
                        audioChannels.add(
                            when (stream.channelLayout) {
                                AudioChannel.CH_2_1.raw -> AudioChannel.CH_2_1
                                AudioChannel.CH_5_1.raw -> AudioChannel.CH_5_1
                                AudioChannel.CH_7_1.raw -> AudioChannel.CH_7_1
                                else -> AudioChannel.CH_2_0
                            }
                        )

                        /**
                         * Match [org.jellyfin.sdk.model.api.MediaStream.displayTitle] for Dolby
                         * Atmos
                         */
                        stream.displayTitle?.apply { isAtmosAudio.add(contains("ATMOS", true)) }

                        /** Match audio codec from [org.jellyfin.sdk.model.api.MediaStream.codec] */
                        audioCodecs.add(
                            when (stream.codec.lowercase()) {
                                AudioCodec.FLAC.toString() -> AudioCodec.FLAC
                                AudioCodec.MP3.toString() -> AudioCodec.MP3
                                AudioCodec.AAC.toString() -> AudioCodec.AAC
                                AudioCodec.AC3.toString() -> AudioCodec.AC3
                                AudioCodec.EAC3.toString() -> AudioCodec.EAC3
                                AudioCodec.VORBIS.toString() -> AudioCodec.VORBIS
                                AudioCodec.OPUS.toString() -> AudioCodec.OPUS
                                AudioCodec.TRUEHD.toString() -> AudioCodec.TRUEHD
                                AudioCodec.DTS.toString() -> AudioCodec.DTS
                                else -> null
                            }
                        )
                    }

                    MediaStreamType.VIDEO -> {
                        with(stream) {
                            displayTitle?.let { videoTracks.add(it) }

                            /**
                             * Match dynamic range from
                             * [org.jellyfin.sdk.model.api.MediaStream.videoRangeType]
                             */
                            when (videoRangeType) {
                                VideoRangeType.SDR -> DisplayProfile.SDR
                                VideoRangeType.HDR10 -> DisplayProfile.HDR10
                                VideoRangeType.HDR10_PLUS -> DisplayProfile.HDR10_PLUS
                                VideoRangeType.HLG -> DisplayProfile.HLG
                                VideoRangeType.DOVI,
                                VideoRangeType.DOVI_WITH_EL,
                                VideoRangeType.DOVI_WITH_ELHDR10_PLUS,
                                VideoRangeType.DOVI_WITH_HLG,
                                VideoRangeType.DOVI_WITH_SDR,
                                VideoRangeType.DOVI_WITH_HDR10,
                                VideoRangeType.DOVI_WITH_HDR10_PLUS -> DisplayProfile.DOLBY_VISION
                                else -> null
                            }?.let { displayProfiles.add(it) }

                            /**
                             * Force stream [org.jellyfin.sdk.model.api.MediaStream.height] and
                             * [org.jellyfin.sdk.model.api.MediaStream.width] as not null since we
                             * are inside [MediaStreamType.VIDEO] block
                             */
                            val h = height ?: 0
                            val w = width ?: 0
                            resolution.add(
                                when {
                                    h >= 1440 || w >= 2560 -> Resolution.UHD
                                    h >= 900 || w >= 1600 -> Resolution.FHD
                                    h >= 600 || w >= 1100 -> Resolution.HD
                                    else -> Resolution.SD
                                }
                            )

                            videoCodecs.add(
                                when (stream.codec.lowercase()) {
                                    VideoCodec.H264.toString() -> VideoCodec.H264
                                    VideoCodec.HEVC.toString() -> VideoCodec.HEVC
                                    VideoCodec.VVC.toString() -> VideoCodec.VVC
                                    VideoCodec.AV1.toString() -> VideoCodec.AV1
                                    else -> null
                                }
                            )
                        }
                    }

                    MediaStreamType.SUBTITLE -> {
                        stream.displayTitle?.let { subtitleTracks.add(it) }
                    }

                    else -> {}
                }
            }
        }

        return VideoMetadata(
            size = source.size,
            videoTracks = videoTracks,
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
            resolution = resolution,
            videoCodecs = videoCodecs.toSet().toList(),
            displayProfiles = displayProfiles.toSet().toList(),
            audioChannels = audioChannels.toSet().toList(),
            audioCodecs = audioCodecs.toSet().toList(),
            isAtmos = isAtmosAudio,
        )
    }
}

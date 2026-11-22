package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.repository.JellyfinRepository
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRangeType

data class JellyxMediaStream(
    val title: String,
    val displayTitle: String?,
    val language: String,
    val type: MediaStreamType,
    val codec: String,
    val isExternal: Boolean,
    val path: String?,
    val channelLayout: String?,
    val videoRangeType: VideoRangeType?,
    val height: Int?,
    val width: Int?,
    val videoDoViTitle: String?,
    val bitRate: Int? = null,
    val profile: String? = null,
)

fun MediaStream.toJellyxMediaStream(jellyfinRepository: JellyfinRepository): JellyxMediaStream {
    return JellyxMediaStream(
        title = title.orEmpty(),
        displayTitle = displayTitle,
        language = language.orEmpty(),
        type = type,
        codec = codec.orEmpty(),
        isExternal = isExternal,
        path =
            deliveryUrl?.let { url ->
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    url
                } else {
                    "${jellyfinRepository.getBaseUrl().trimEnd('/')}/${url.trimStart('/')}"
                }
            },
        channelLayout = channelLayout,
        videoRangeType = videoRangeType,
        height = height,
        width = width,
        videoDoViTitle = videoDoViTitle,
        bitRate = bitRate,
        profile = profile,
    )
}

fun JellyxMediaStreamDto.toJellyxMediaStream(): JellyxMediaStream {
    return JellyxMediaStream(
        title = title,
        displayTitle = displayTitle,
        language = language,
        type = type,
        codec = codec,
        isExternal = isExternal,
        path = path,
        channelLayout = channelLayout,
        videoRangeType = VideoRangeType.fromNameOrNull(videoRangeType ?: ""),
        height = height,
        width = width,
        videoDoViTitle = videoDoViTitle,
    )
}

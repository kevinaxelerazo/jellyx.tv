package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.io.File
import java.util.UUID
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo

data class JellyxSource(
    val id: String,
    val name: String,
    val type: JellyxSourceType,
    val path: String,
    val size: Long,
    val mediaStreams: List<JellyxMediaStream>,
    val downloadId: Long? = null,
    val bitrate: Int? = null,
    val liveStreamId: String? = null,
)

suspend fun MediaSourceInfo.toJellyxSource(
    jellyfinRepository: JellyfinRepository,
    itemId: UUID,
    includePath: Boolean = false,
): JellyxSource {
    val baseUrl = jellyfinRepository.getBaseUrl().trimEnd('/')
    val resolvedTranscodingUrl =
        transcodingUrl?.let { url ->
            if (url.startsWith("http://") || url.startsWith("https://")) {
                url
            } else {
                "$baseUrl/${url.trimStart('/')}"
            }
        }
    val path =
        when (protocol) {
            MediaProtocol.FILE -> {
                try {
                    if (!includePath) {
                        ""
                    } else {
                        jellyfinRepository.getStreamUrl(itemId, id.orEmpty())
                    }
                } catch (_: Exception) {
                    resolvedTranscodingUrl.orEmpty()
                }
            }
            MediaProtocol.HTTP -> {
                val raw = this.path.orEmpty().trim()
                val isInternalHost =
                    raw.contains("://threadfin", ignoreCase = true) ||
                        raw.contains("://localhost", ignoreCase = true) ||
                        raw.contains("://127.0.0.1") ||
                        raw.contains(":34400") ||
                        Regex("://(10\\.|192\\.168\\.|172\\.(1[6-9]|2[0-9]|3[0-1])\\.)")
                            .containsMatchIn(raw)
                when {
                    // External public HTTP/HTTPS HLS stream (e.g., Threadfin No-Buffer passthrough)
                    !isInternalHost && (raw.startsWith("http://") || raw.startsWith("https://")) ->
                        raw
                    // Internal Docker/Threadfin host proxied through Jellyfin Direct Stream / HLS copy
                    !resolvedTranscodingUrl.isNullOrBlank() ->
                        resolvedTranscodingUrl
                    !liveStreamId.isNullOrBlank() && isInternalHost ->
                        "$baseUrl/LiveTv/LiveStreamFiles/$liveStreamId/stream.ts"
                    raw.startsWith("/") -> "$baseUrl/${raw.trimStart('/')}"
                    else -> raw
                }
            }
            else -> resolvedTranscodingUrl.orEmpty()
        }
    return JellyxSource(
        id = id.orEmpty(),
        name = name.orEmpty(),
        type = JellyxSourceType.REMOTE,
        path = path,
        size = size ?: 0,
        mediaStreams =
            mediaStreams?.map { it.toJellyxMediaStream(jellyfinRepository) } ?: emptyList(),
        bitrate = bitrate,
        liveStreamId = liveStreamId,
    )
}

suspend fun JellyxSourceDto.toJellyxSource(
    serverDatabaseDao: ServerDatabaseDao
): JellyxSource {
    return JellyxSource(
        id = id,
        name = name,
        type = type,
        path = path,
        size = File(path).length(),
        mediaStreams =
            serverDatabaseDao.getMediaStreamsBySourceId(id).map { it.toJellyxMediaStream() },
        downloadId = downloadId,
    )
}

enum class JellyxSourceType {
    REMOTE,
    LOCAL,
}

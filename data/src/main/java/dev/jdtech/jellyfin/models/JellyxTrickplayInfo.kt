package dev.jdtech.jellyfin.models

import org.jellyfin.sdk.model.api.TrickplayInfoDto

data class JellyxTrickplayInfo(
    val width: Int,
    val height: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val thumbnailCount: Int,
    val interval: Int,
    val bandwidth: Int,
)

fun TrickplayInfoDto.toJellyxTrickplayInfo(): JellyxTrickplayInfo {
    return JellyxTrickplayInfo(
        width = width,
        height = height,
        tileWidth = tileWidth,
        tileHeight = tileHeight,
        thumbnailCount = thumbnailCount,
        interval = interval,
        bandwidth = bandwidth,
    )
}

fun JellyxTrickplayInfoDto.toJellyxTrickplayInfo(): JellyxTrickplayInfo {
    return JellyxTrickplayInfo(
        width = width,
        height = height,
        tileWidth = tileWidth,
        tileHeight = tileHeight,
        thumbnailCount = thumbnailCount,
        interval = interval,
        bandwidth = bandwidth,
    )
}

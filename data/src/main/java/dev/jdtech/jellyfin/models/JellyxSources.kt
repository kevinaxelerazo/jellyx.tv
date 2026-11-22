package dev.jdtech.jellyfin.models

interface JellyxSources {
    val sources: List<JellyxSource>
    val runtimeTicks: Long
    val trickplayInfo: Map<String, JellyxTrickplayInfo>?
}

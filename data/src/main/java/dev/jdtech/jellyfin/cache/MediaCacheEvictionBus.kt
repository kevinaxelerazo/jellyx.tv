package dev.jdtech.jellyfin.cache

import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

fun interface MediaCacheEvictionListener {
    fun onEvictItem(itemId: UUID)
}

class MediaCacheEvictionBus {
    private val listeners = CopyOnWriteArrayList<MediaCacheEvictionListener>()

    fun registerListener(listener: MediaCacheEvictionListener) {
        listeners.addIfAbsent(listener)
    }

    fun unregisterListener(listener: MediaCacheEvictionListener) {
        listeners.remove(listener)
    }

    fun requestEviction(itemId: UUID) {
        listeners.forEach { it.onEvictItem(itemId) }
    }
}

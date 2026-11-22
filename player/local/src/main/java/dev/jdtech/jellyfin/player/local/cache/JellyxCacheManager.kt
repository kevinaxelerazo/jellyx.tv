package dev.jdtech.jellyfin.player.local.cache

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.AesCipherDataSink
import androidx.media3.datasource.AesCipherDataSource
import androidx.media3.datasource.DataSink
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.cache.MediaCacheEvictionBus
import dev.jdtech.jellyfin.cache.MediaCacheEvictionListener
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@UnstableApi
@Singleton
class JellyxCacheManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    cacheEvictionBus: MediaCacheEvictionBus,
) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheLock = Any()
    private val secretKey: ByteArray by lazy { loadOrCreateSecretKey(context) }

    val isTelevisionDevice: Boolean by lazy {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager?
        val isTvMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback =
            context.packageManager?.hasSystemFeature(PackageManager.FEATURE_LEANBACK) == true
        isTvMode || hasLeanback
    }

    private val maxCacheBytes: Long by lazy {
        if (isTelevisionDevice) TV_MAX_CACHE_BYTES else MOBILE_MAX_CACHE_BYTES
    }

    private val lastItemMarkerFile: File
        get() = File(context.cacheDir, LAST_ITEM_MARKER_FILE)

    @Volatile private var simpleCacheInstance: SimpleCache? = null

    val simpleCache: SimpleCache
        get() =
            simpleCacheInstance
                ?: synchronized(cacheLock) {
                    simpleCacheInstance
                        ?: run {
                            val cacheDir =
                                File(context.cacheDir, SESSION_CACHE_DIR_NAME).apply { mkdirs() }
                            val evictor = LeastRecentlyUsedCacheEvictor(maxCacheBytes)
                            val dbProvider = StandaloneDatabaseProvider(context)
                            SimpleCache(cacheDir, evictor, dbProvider, secretKey, true, true).also {
                                simpleCacheInstance = it
                            }
                        }
                }

    private val evictionListener = MediaCacheEvictionListener { itemId -> evictItem(itemId) }

    init {
        cacheEvictionBus.registerListener(evictionListener)
        ioScope.launch { deleteLegacyCacheDir() }
    }

    fun buildCacheDataSourceFactory(upstreamFactory: DataSource.Factory): DataSource.Factory {
        val cacheReadFactory = DataSource.Factory {
            AesCipherDataSource(secretKey, FileDataSource())
        }
        val cacheWriteFactory = DataSink.Factory {
            AesCipherDataSink(
                secretKey,
                CacheDataSink(simpleCache, CacheDataSink.DEFAULT_FRAGMENT_SIZE),
                ByteArray(SCRATCH_BUFFER_SIZE_BYTES),
            )
        }
        val cacheDataSourceFactory =
            CacheDataSource.Factory()
                .setCache(simpleCache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setCacheReadDataSourceFactory(cacheReadFactory)
                .setCacheWriteDataSinkFactory(cacheWriteFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return DataSource.Factory {
            LiveTvBypassDataSource(
                directSource = upstreamFactory.createDataSource(),
                cachedSource = cacheDataSourceFactory.createDataSource(),
            )
        }
    }

    fun evictItem(itemId: UUID) {
        ioScope.launch {
            try {
                val activeCache = simpleCacheInstance ?: return@launch
                val idToken = itemId.toString()
                val matchingKeys =
                    activeCache.keys.filter { key -> key.contains(idToken, ignoreCase = true) }
                matchingKeys.forEach { key -> activeCache.removeResource(key) }
                if (matchingKeys.isNotEmpty()) {
                    Timber.d("Evicted %d cached keys for item %s", matchingKeys.size, itemId)
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to evict cached media for %s", itemId)
            }
        }
    }

    suspend fun prepareForPlayback(itemId: UUID?) =
        withContext(Dispatchers.IO) {
            synchronized(cacheLock) {
                val previousItemId = readLastCachedItemId()
                if (itemId != null && itemId == previousItemId) {
                    Timber.d("Reusing existing playback cache for item %s", itemId)
                    return@withContext
                }

                val targetToken = itemId?.toString()
                runCatching {
                    val activeCache = simpleCacheInstance
                    if (activeCache != null) {
                        val keysToRemove =
                            activeCache.keys.filter { key ->
                                targetToken == null || !key.contains(targetToken, ignoreCase = true)
                            }
                        keysToRemove.forEach { key -> runCatching { activeCache.removeResource(key) } }
                        if (keysToRemove.isNotEmpty()) {
                            Timber.d(
                                "Purged %d stale cache entries (previous=%s, current=%s)",
                                keysToRemove.size,
                                previousItemId,
                                itemId,
                            )
                        }
                    } else {
                        File(context.cacheDir, SESSION_CACHE_DIR_NAME).deleteRecursively()
                    }
                }.onFailure { Timber.w(it, "Failed to purge stale playback cache") }

                runCatching { File(context.cacheDir, MPV_CACHE_DIR_NAME).deleteRecursively() }
                writeLastCachedItemId(itemId)
                deleteLegacyCacheDir()
            }
        }

    fun clearSessionCache(includeMpvCache: Boolean = true) {
        ioScope.launch {
            synchronized(cacheLock) {
                runCatching {
                    val activeCache = simpleCacheInstance
                    if (activeCache != null) {
                        val keys = activeCache.keys.toList()
                        keys.forEach { key -> runCatching { activeCache.removeResource(key) } }
                        if (keys.isNotEmpty()) {
                            Timber.d("Cleared %d session media cache entries", keys.size)
                        }
                    } else {
                        File(context.cacheDir, SESSION_CACHE_DIR_NAME).deleteRecursively()
                    }
                }.onFailure { Timber.w(it, "Failed to clear session media cache") }

                if (includeMpvCache) {
                    runCatching { File(context.cacheDir, MPV_CACHE_DIR_NAME).deleteRecursively() }
                }
                writeLastCachedItemId(null)
                deleteLegacyCacheDir()
            }
        }
    }

    private fun readLastCachedItemId(): UUID? {
        return runCatching {
            val file = lastItemMarkerFile
            if (!file.exists()) return null
            val raw = file.readText().trim()
            if (raw.isEmpty()) null else UUID.fromString(raw)
        }.getOrNull()
    }

    private fun writeLastCachedItemId(itemId: UUID?) {
        runCatching {
            val file = lastItemMarkerFile
            if (itemId == null) {
                file.delete()
            } else {
                file.parentFile?.mkdirs()
                file.writeText(itemId.toString())
            }
        }.onFailure { Timber.w(it, "Failed to persist last cached item marker") }
    }

    private fun deleteLegacyCacheDir() {
        runCatching {
            val legacyDir = File(context.noBackupFilesDir, LEGACY_CACHE_DIR_NAME)
            if (legacyDir.exists()) {
                legacyDir.deleteRecursively()
                Timber.d("Removed legacy media cache directory: %s", legacyDir.absolutePath)
            }
        }.onFailure { Timber.w(it, "Failed to remove legacy media cache directory") }
    }

    private fun loadOrCreateSecretKey(context: Context): ByteArray {
        val keyFile = File(context.noBackupFilesDir, KEY_FILE_NAME)
        if (keyFile.exists()) {
            val bytes = keyFile.readBytes()
            if (bytes.size == AES_KEY_BYTES) return bytes
        }
        val key = ByteArray(AES_KEY_BYTES)
        SecureRandom().nextBytes(key)
        keyFile.parentFile?.mkdirs()
        keyFile.writeBytes(key)
        return key
    }

    private class LiveTvBypassDataSource(
        private val directSource: DataSource,
        private val cachedSource: DataSource,
    ) : DataSource {
        private var activeSource: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            directSource.addTransferListener(transferListener)
            cachedSource.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val url = dataSpec.uri.toString().lowercase()
            val isLiveStream =
                url.contains(".m3u8") ||
                    url.contains(".ts") ||
                    url.contains("/livetv/") ||
                    url.contains("livestreamid=") ||
                    url.contains(":34400") ||
                    url.contains(":8888") ||
                    url.contains(":9002") ||
                    url.contains(":9999")
            val scheme = dataSpec.uri.scheme?.lowercase()
            val isLocalFile =
                scheme == null || scheme == "file" || scheme == "content" || url.startsWith("/")
            val chosen = if (isLiveStream || isLocalFile) directSource else cachedSource
            activeSource = chosen
            return chosen.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            return activeSource?.read(buffer, offset, length) ?: -1
        }

        override fun getUri(): Uri? = activeSource?.uri

        override fun getResponseHeaders(): Map<String, List<String>> =
            activeSource?.responseHeaders ?: emptyMap()

        override fun close() {
            try {
                activeSource?.close()
            } finally {
                activeSource = null
            }
        }
    }

    companion object {
        private const val SESSION_CACHE_DIR_NAME = "jellyx_session_cache_v1"
        private const val LAST_ITEM_MARKER_FILE = "jellyx_last_cached_item.id"
        private const val LEGACY_CACHE_DIR_NAME = "jellyx_media_cache_v2"
        private const val MPV_CACHE_DIR_NAME = "mpv"
        private const val KEY_FILE_NAME = "jellyx_cache.key"
        private const val AES_KEY_BYTES = 16
        private const val SCRATCH_BUFFER_SIZE_BYTES = 16 * 1024
        private const val TV_MAX_CACHE_BYTES = 150L * 1024L * 1024L // 150 MB cap on TV
        private const val MOBILE_MAX_CACHE_BYTES = 300L * 1024L * 1024L // 300 MB cap on Mobile
    }
}

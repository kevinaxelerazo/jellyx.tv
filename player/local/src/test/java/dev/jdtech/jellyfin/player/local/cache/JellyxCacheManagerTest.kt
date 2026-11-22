package dev.jdtech.jellyfin.player.local.cache

import android.content.Context
import dev.jdtech.jellyfin.cache.MediaCacheEvictionBus
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JellyxCacheManagerTest {
    @Rule @JvmField val tempFolder = TemporaryFolder()

    private lateinit var cacheDir: File
    private lateinit var noBackupDir: File
    private lateinit var context: Context

    @Before
    fun setUp() {
        cacheDir = tempFolder.newFolder("cache")
        noBackupDir = tempFolder.newFolder("no_backup")
        context = mockk(relaxed = true)
        every { context.cacheDir } returns cacheDir
        every { context.noBackupFilesDir } returns noBackupDir
    }

    @Test
    fun `prepareForPlayback keeps cache across relaunches for same item and purges when switching content`() =
        runTest {
            val itemA = UUID.randomUUID()
            val itemB = UUID.randomUUID()

            val firstSession = JellyxCacheManager(context, MediaCacheEvictionBus())
            firstSession.prepareForPlayback(itemA)

            val cachedChunk = File(cacheDir, "jellyx_session_cache_v1/chunk.exo").apply {
                parentFile?.mkdirs()
                writeText("cached-blocks")
            }
            val mpvTemp = File(cacheDir, "mpv/shader.bin").apply {
                parentFile?.mkdirs()
                writeText("mpv-cache")
            }
            val offlineDownload = File(noBackupDir, "offline_cache/movie.jellyx").apply {
                parentFile?.mkdirs()
                writeText("voluntary-download")
            }

            // Relaunch and resume same item: blocks must stay intact
            val sameItemRelaunch = JellyxCacheManager(context, MediaCacheEvictionBus())
            sameItemRelaunch.prepareForPlayback(itemA)
            assertTrue(cachedChunk.exists())
            assertTrue(mpvTemp.exists())
            assertTrue(offlineDownload.exists())

            // Switch to a different content item: streaming cache must purge automatically,
            // while voluntary offline_cache stays untouched
            val differentItemRelaunch = JellyxCacheManager(context, MediaCacheEvictionBus())
            differentItemRelaunch.prepareForPlayback(itemB)
            assertFalse(cachedChunk.exists())
            assertFalse(mpvTemp.exists())
            assertTrue(offlineDownload.exists())
        }
}


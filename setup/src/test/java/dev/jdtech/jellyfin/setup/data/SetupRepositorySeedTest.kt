package dev.jdtech.jellyfin.setup.data

import android.content.SharedPreferences
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.Server
import dev.jdtech.jellyfin.models.ServerAddress
import dev.jdtech.jellyfin.models.ServerWithAddresses
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SetupRepositorySeedTest {
    private lateinit var jellyfinApi: JellyfinApi
    private lateinit var database: ServerDatabaseDao
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var appPreferences: AppPreferences
    private lateinit var prefsMap: MutableMap<String, Any?>

    @Before
    fun setUp() {
        jellyfinApi = mockk(relaxed = true)
        database = mockk(relaxed = true)
        prefsMap = mutableMapOf()
        editor = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)

        every { sharedPreferences.edit() } returns editor
        every { editor.putBoolean(any(), any()) } answers
            {
                prefsMap[firstArg()] = secondArg<Boolean>()
                editor
            }
        every { editor.putString(any(), any()) } answers
            {
                prefsMap[firstArg()] = secondArg<String?>()
                editor
            }
        every { sharedPreferences.getBoolean(any(), any()) } answers
            {
                (prefsMap[firstArg()] as? Boolean) ?: secondArg()
            }
        every { sharedPreferences.getString(any(), any()) } answers
            {
                (prefsMap[firstArg()] as? String?) ?: secondArg()
            }

        appPreferences = AppPreferences(sharedPreferences)
    }

    @Test
    fun seedDefaultServer_whenAlreadySeeded_returnsFalseAndDoesNotQueryDatabase() = runTest {
        appPreferences.setValue(appPreferences.defaultServerSeeded, true)
        val repository = SetupRepositoryImpl(jellyfinApi, database, appPreferences)

        val result =
            repository.seedDefaultServer(
                listOf("https://jellyx.example.com", "http://203.0.113.10:8096")
            )

        assertFalse(result)
        coVerify(exactly = 0) { database.getServersCount() }
    }

    @Test
    fun seedDefaultServer_whenServersAlreadyExist_marksSeededAndReturnsFalse() = runTest {
        coEvery { database.getServersCount() } returns 1
        val repository = SetupRepositoryImpl(jellyfinApi, database, appPreferences)

        val result =
            repository.seedDefaultServer(
                listOf("https://jellyx.example.com", "http://203.0.113.10:8096")
            )

        assertFalse(result)
        assertTrue(appPreferences.getValue(appPreferences.defaultServerSeeded))
        assertTrue(appPreferences.getValue(appPreferences.localNetworkPermissionDecided))
    }

    @Test
    fun seedDefaultServer_whenPrimaryUrlFails_fallsBackToSecondaryUrlAndInsertsBothAddresses() =
        runTest {
            val primaryUrl = "https://jellyx.example.com"
            val fallbackUrl = "http://203.0.113.10:8096"
            val serverId = "server-123"
            val addressId = UUID.randomUUID()
            val server =
                Server(
                    id = serverId,
                    name = "Jellyx.tv",
                    currentServerAddressId = addressId,
                    currentUserId = null,
                )

            coEvery { database.getServersCount() } returns 0
            coEvery { database.getServerWithAddresses(serverId) } returns
                ServerWithAddresses(
                    server = server,
                    addresses = listOf(ServerAddress(addressId, serverId, fallbackUrl)),
                    user = null,
                )

            val repository = spyk(SetupRepositoryImpl(jellyfinApi, database, appPreferences))
            coEvery { repository.addServer(primaryUrl) } throws RuntimeException("DNS unreachable")
            coEvery { repository.addServer(fallbackUrl) } returns server
            coEvery { repository.setCurrentServer(serverId) } returns Unit

            val insertedAddressSlot = slot<ServerAddress>()
            coEvery { database.insertServerAddress(capture(insertedAddressSlot)) } returns Unit

            val result = repository.seedDefaultServer(listOf(primaryUrl, fallbackUrl))

            assertTrue(result)
            coVerify(exactly = 1) { repository.addServer(primaryUrl) }
            coVerify(exactly = 1) { repository.addServer(fallbackUrl) }
            coVerify(exactly = 1) { database.insertServerAddress(any()) }
            assertEquals(primaryUrl, insertedAddressSlot.captured.address)
            assertEquals(serverId, appPreferences.getValue(appPreferences.currentServer))
            assertTrue(appPreferences.getValue(appPreferences.defaultServerSeeded))
            assertTrue(appPreferences.getValue(appPreferences.localNetworkPermissionDecided))
        }

    @Test
    fun seedDefaultServer_whenAllDiscoveryCallsFail_seedsOfflineServerIntoDatabase() = runTest {
        val fallbackUrl = "http://203.0.113.10:8096"
        coEvery { database.getServersCount() } returns 0
        coEvery { database.getServerWithAddresses(any()) } answers
            {
                val id = firstArg<String>()
                ServerWithAddresses(
                    server =
                        Server(
                            id = id,
                            name = "Jellyx.tv",
                            currentServerAddressId = UUID.randomUUID(),
                            currentUserId = null,
                        ),
                    addresses = listOf(ServerAddress(UUID.randomUUID(), id, fallbackUrl)),
                    user = null,
                )
            }

        val repository = spyk(SetupRepositoryImpl(jellyfinApi, database, appPreferences))
        coEvery { repository.addServer(fallbackUrl) } throws RuntimeException("Timeout on cold Wi-Fi")
        coEvery { repository.setCurrentServer(any()) } returns Unit

        val insertedServerSlot = slot<Server>()
        coEvery { database.insertServer(capture(insertedServerSlot)) } returns Unit

        val result = repository.seedDefaultServer(listOf("", fallbackUrl), "Jellyx.tv")

        assertTrue(result)
        assertEquals("Jellyx.tv", insertedServerSlot.captured.name)
        assertEquals(
            insertedServerSlot.captured.id,
            appPreferences.getValue(appPreferences.currentServer),
        )
        assertTrue(appPreferences.getValue(appPreferences.defaultServerSeeded))
        assertTrue(appPreferences.getValue(appPreferences.localNetworkPermissionDecided))
    }
}

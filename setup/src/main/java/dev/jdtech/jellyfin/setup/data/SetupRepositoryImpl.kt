package dev.jdtech.jellyfin.setup.data

import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.data.BuildConfig
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.ExceptionUiText
import dev.jdtech.jellyfin.models.ExceptionUiTexts
import dev.jdtech.jellyfin.models.Server
import dev.jdtech.jellyfin.models.ServerAddress
import dev.jdtech.jellyfin.models.ServerWithAddresses
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.setup.R as SetupR
import dev.jdtech.jellyfin.setup.domain.SetupRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.discovery.RecommendedServerInfo
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.discovery.RecommendedServerIssue
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.QuickConnectDto
import org.jellyfin.sdk.model.api.QuickConnectResult
import org.jellyfin.sdk.model.api.ServerDiscoveryInfo
import timber.log.Timber

class SetupRepositoryImpl(
    private val jellyfinApi: JellyfinApi,
    private val database: ServerDatabaseDao,
    private val appPreferences: AppPreferences,
) : SetupRepository {
    override fun discoverServers(): Flow<ServerDiscoveryInfo> {
        return jellyfinApi.jellyfin.discovery.discoverLocalServers()
    }

    override suspend fun getServers(): List<ServerWithAddresses> {
        return database.getServersWithAddresses()
    }

    override suspend fun getCurrentServer(): Server? {
        return appPreferences.getValue(appPreferences.currentServer)?.let { id ->
            database.getServer(id)
        }
    }

    override suspend fun deleteServer(serverId: String) {
        database.deleteServer(serverId)
    }

    override suspend fun getIsQuickConnectEnabled(): Boolean =
        withContext(Dispatchers.IO) {
            jellyfinApi.authenticationApi.getQuickConnectEnabled().content
        }

    override suspend fun initiateQuickConnect(): QuickConnectResult =
        withContext(Dispatchers.IO) { jellyfinApi.authenticationApi.initiateQuickConnect().content }

    override suspend fun getQuickConnectState(secret: String): QuickConnectResult =
        withContext(Dispatchers.IO) {
            jellyfinApi.authenticationApi.getQuickConnectState(secret).content
        }

    override suspend fun setCurrentServer(serverId: String) {
        val serverWithAddressAndUser = database.getServerWithAddressAndUser(serverId) ?: return
        val serverAddress = serverWithAddressAndUser.address ?: return
        val user = serverWithAddressAndUser.user

        jellyfinApi.apply {
            api.update(baseUrl = serverAddress.address, accessToken = user?.accessToken)
            userId = user?.id
        }
    }

    override suspend fun addServer(address: String): Server {
        // Check if address is not blank
        if (address.isBlank()) {
            throw ExceptionUiText(
                UiText.StringResource(SetupR.string.add_server_error_empty_address)
            )
        }

        val candidates = jellyfinApi.jellyfin.discovery.getAddressCandidates(address)
        val recommended =
            jellyfinApi.jellyfin.discovery.getRecommendedServers(
                candidates,
                RecommendedServerInfoScore.OK,
            )
        val goodServers = mutableListOf<RecommendedServerInfo>()
        val okServers = mutableListOf<RecommendedServerInfo>()

        for (recommendedServerInfo in recommended) {
            when (recommendedServerInfo.score) {
                RecommendedServerInfoScore.GREAT -> {
                    return saveServerInDatabase(recommendedServerInfo)
                }
                RecommendedServerInfoScore.GOOD -> goodServers.add(recommendedServerInfo)
                RecommendedServerInfoScore.OK -> okServers.add(recommendedServerInfo)
                RecommendedServerInfoScore.BAD -> Unit
            }
        }

        when {
            goodServers.isNotEmpty() -> {
                return saveServerInDatabase(goodServers.first())
            }
            okServers.isNotEmpty() -> {
                val okServer = okServers.first()
                val hasFatalIssue =
                    okServer.issues.any {
                        it is RecommendedServerIssue.InvalidProductName ||
                            it is RecommendedServerIssue.UnsupportedServerVersion
                    }
                if (!hasFatalIssue && okServer.systemInfo.getOrNull()?.id != null) {
                    return saveServerInDatabase(okServer)
                }
                throw ExceptionUiTexts(createIssuesString(okServer))
            }
            else -> {
                throw ExceptionUiText(
                    UiText.StringResource(SetupR.string.add_server_error_not_found)
                )
            }
        }
    }

    override suspend fun seedDefaultServer(): Boolean =
        seedDefaultServer(listOf(BuildConfig.DEFAULT_SERVER_URL, BuildConfig.FALLBACK_SERVER_URL))

    internal suspend fun seedDefaultServer(
        rawUrls: List<String>,
        defaultServerName: String = BuildConfig.DEFAULT_SERVER_NAME.ifBlank { "Jellyx.tv" },
    ): Boolean =
        withContext(Dispatchers.IO) {
            seedMutex.withLock {
                if (appPreferences.getValue(appPreferences.defaultServerSeeded)) {
                    return@withLock false
                }

                val urls = rawUrls.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                if (urls.isEmpty()) {
                    return@withLock false
                }

                if (database.getServersCount() > 0) {
                    appPreferences.setValue(appPreferences.defaultServerSeeded, true)
                    appPreferences.setValue(appPreferences.localNetworkPermissionDecided, true)
                    return@withLock false
                }

                var server: Server? = null
                for (url in urls) {
                    try {
                        server = addServer(url)
                        break
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(
                            "Default server discovery attempt failed (%s)",
                            e::class.simpleName,
                        )
                    }
                }

                val seededServer =
                    server
                        ?: createOfflineSeededServer(
                            primaryUrl = urls.first(),
                            serverName = defaultServerName,
                        )

                val existingAddresses =
                    database
                        .getServerWithAddresses(seededServer.id)
                        ?.addresses
                        .orEmpty()
                        .map { it.address.trimEnd('/') }
                        .toSet()
                for (url in urls) {
                    if (url.trimEnd('/') !in existingAddresses) {
                        database.insertServerAddress(
                            ServerAddress(
                                id = UUID.randomUUID(),
                                serverId = seededServer.id,
                                address = url,
                            )
                        )
                    }
                }

                setCurrentServer(seededServer.id)
                appPreferences.setValue(appPreferences.currentServer, seededServer.id)
                appPreferences.setValue(appPreferences.defaultServerSeeded, true)
                appPreferences.setValue(appPreferences.localNetworkPermissionDecided, true)
                true
            }
        }

    private suspend fun createOfflineSeededServer(
        primaryUrl: String,
        serverName: String,
    ): Server {
        val fallbackId =
            UUID.nameUUIDFromBytes("jellyx-seed:${primaryUrl.trimEnd('/')}".toByteArray())
                .toString()
        val addressId = UUID.randomUUID()
        val serverAddress =
            ServerAddress(
                id = addressId,
                serverId = fallbackId,
                address = primaryUrl,
            )
        val server =
            Server(
                id = fallbackId,
                name = serverName,
                currentServerAddressId = addressId,
                currentUserId = null,
            )
        database.insertServer(server)
        database.insertServerAddress(serverAddress)
        jellyfinApi.apply {
            api.update(baseUrl = primaryUrl, accessToken = null)
        }
        return server
    }

    companion object {
        private val seedMutex = Mutex()
    }

    private suspend fun saveServerInDatabase(recommendedServerInfo: RecommendedServerInfo): Server {
        val serverInfo =
            recommendedServerInfo.systemInfo.getOrNull()
                ?: throw ExceptionUiText(
                    UiText.StringResource(SetupR.string.add_server_error_no_id)
                )

        Timber.d("Connecting to server: ${serverInfo.serverName}")

        val serverInDatabase = database.getServer(serverInfo.id!!)

        // Check if server is already in the database
        // If so only add a new address to that server if it's different
        val server =
            if (serverInDatabase != null) {
                val addresses = database.getServerWithAddresses(serverInDatabase.id).addresses
                // If address is not in database, add it
                if (addresses.none { it.address == recommendedServerInfo.address }) {
                    val serverAddress =
                        ServerAddress(
                            id = UUID.randomUUID(),
                            serverId = serverInDatabase.id,
                            address = recommendedServerInfo.address,
                        )

                    database.insertServerAddress(serverAddress)
                }
                serverInDatabase
            } else {
                val serverAddress =
                    ServerAddress(
                        id = UUID.randomUUID(),
                        serverId = serverInfo.id!!,
                        address = recommendedServerInfo.address,
                    )

                val server =
                    Server(
                        id = serverInfo.id!!,
                        name = serverInfo.serverName!!,
                        currentServerAddressId = serverAddress.id,
                        currentUserId = null,
                    )

                database.insertServer(server)
                database.insertServerAddress(serverAddress)
                server
            }

        jellyfinApi.apply {
            api.update(baseUrl = recommendedServerInfo.address, accessToken = null)
        }

        return server
    }

    /**
     * Create a presentable string of issues with a server
     *
     * @param server The server with issues
     * @return A presentable string of issues separated with \n
     */
    private fun createIssuesString(server: RecommendedServerInfo): Collection<UiText> {
        return server.issues.map {
            when (it) {
                is RecommendedServerIssue.OutdatedServerVersion -> {
                    UiText.StringResource(SetupR.string.add_server_error_outdated, it.version)
                }
                is RecommendedServerIssue.InvalidProductName -> {
                    UiText.StringResource(
                        SetupR.string.add_server_error_not_jellyfin,
                        it.productName ?: "",
                    )
                }
                is RecommendedServerIssue.UnsupportedServerVersion -> {
                    UiText.StringResource(SetupR.string.add_server_error_version, it.version)
                }
                is RecommendedServerIssue.SlowResponse -> {
                    UiText.StringResource(SetupR.string.add_server_error_slow, it.responseTime)
                }
                else -> {
                    UiText.StringResource(CoreR.string.unknown_error)
                }
            }
        }
    }

    override suspend fun loadDisclaimer(): String? =
        withContext(Dispatchers.IO) {
            jellyfinApi.brandingApi.getBrandingOptions().content.loginDisclaimer
        }

    override suspend fun login(username: String, password: String) {
        withContext(Dispatchers.IO) {
            val authenticationResult by
                jellyfinApi.authenticationApi.authenticateUserByName(
                    data = AuthenticateUserByName(username = username, pw = password)
                )

            saveAuthenticationResult(authenticationResult)
        }
    }

    override suspend fun loginWithSecret(secret: String) {
        withContext(Dispatchers.IO) {
            val authenticationResult by
                jellyfinApi.authenticationApi.authenticateWithQuickConnect(
                    data = QuickConnectDto(secret = secret)
                )

            saveAuthenticationResult(authenticationResult)
        }
    }

    private suspend fun saveAuthenticationResult(authenticationResult: AuthenticationResult) {
        val realServerId = authenticationResult.serverId!!
        if (database.getServer(realServerId) == null) {
            val currentServerId = appPreferences.getValue(appPreferences.currentServer)
            val fallbackServer =
                currentServerId?.let { database.getServer(it) }
                    ?: database.getServers().firstOrNull()
            val fallbackAddresses =
                fallbackServer?.let { database.getServerWithAddresses(it.id).addresses }.orEmpty()
            val primaryAddressId = fallbackAddresses.firstOrNull()?.id ?: UUID.randomUUID()
            val reconciledServer =
                Server(
                    id = realServerId,
                    name =
                        fallbackServer?.name
                            ?: BuildConfig.DEFAULT_SERVER_NAME.ifBlank { "Jellyx.tv" },
                    currentServerAddressId = primaryAddressId,
                    currentUserId = authenticationResult.user!!.id,
                )
            database.insertServer(reconciledServer)
            if (fallbackAddresses.isNotEmpty()) {
                for (addr in fallbackAddresses) {
                    database.insertServerAddress(addr.copy(serverId = realServerId))
                }
            } else {
                val baseUrl = jellyfinApi.api.baseUrl.orEmpty()
                if (baseUrl.isNotBlank()) {
                    database.insertServerAddress(
                        ServerAddress(
                            id = primaryAddressId,
                            serverId = realServerId,
                            address = baseUrl,
                        )
                    )
                }
            }
            if (fallbackServer != null && fallbackServer.id != realServerId) {
                database.deleteServer(fallbackServer.id)
            }
            appPreferences.setValue(appPreferences.currentServer, realServerId)
        }

        val user =
            User(
                id = authenticationResult.user!!.id,
                name = authenticationResult.user!!.name!!,
                serverId = realServerId,
                accessToken = authenticationResult.accessToken!!,
            )

        database.insertUser(user)
        database.updateServerCurrentUser(realServerId, user.id)

        jellyfinApi.apply {
            api.update(accessToken = authenticationResult.accessToken)
            userId = authenticationResult.user?.id
        }
    }

    override suspend fun getUsers(serverId: String): List<User> {
        return database.getUsers(serverId)
    }

    override suspend fun getPublicUsers(serverId: String): List<User> =
        withContext(Dispatchers.IO) {
            jellyfinApi.userApi.getPublicUsers().content.mapNotNull {
                User(id = it.id, name = it.name ?: return@mapNotNull null, serverId = serverId)
            }
        }

    override suspend fun getCurrentUser(): User? {
        val currentServer = getCurrentServer() ?: return null
        return database.getServerCurrentUser(currentServer.id)
    }

    override suspend fun deleteUser(userId: UUID) {
        // Let the user delete the current active user for now
        /*val currentUser = getCurrentUser() ?: return
        if (userId == currentUser.id) {
            Timber.e("You cannot delete the current user")
            return
        }*/
        database.deleteUser(userId)
    }

    override suspend fun setCurrentUser(userId: UUID) {
        val server = getCurrentServer() ?: return
        val user = database.getUser(userId) ?: return
        server.currentUserId = user.id
        database.updateServer(server)

        jellyfinApi.apply {
            api.update(accessToken = user.accessToken)
            this.userId = user.id
        }
    }

    override suspend fun setCurrentAddress(addressId: UUID) {
        val server = getCurrentServer() ?: return
        val address = database.getAddress(id = addressId)
        if (address.serverId != server.id) {
            return
        }
        server.currentServerAddressId = address.id
        database.updateServer(server)
        jellyfinApi.apply { api.update(baseUrl = address.address) }
    }
}

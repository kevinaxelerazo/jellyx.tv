package dev.jdtech.jellyfin.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.Server
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.session.SessionAlertEvent
import dev.jdtech.jellyfin.session.SessionEventManager
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel
@Inject
constructor(
    private val appPreferences: AppPreferences,
    private val database: ServerDatabaseDao,
    private val repositoryProvider: Provider<JellyfinRepository>,
    private val sessionEventManager: SessionEventManager,
    private val jellyfinApi: JellyfinApi,
) : ViewModel() {
    private val _state = MutableStateFlow(MainState())
    val state = _state.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.asStateFlow()

    private var bootstrappedSessionKey: String? = null

    sealed class UiState {
        data class Normal(val server: Server?, val user: User?) : UiState()

        data object Loading : UiState()
    }

    init {
        check()
        observeSessionRevocation()
    }

    fun loadServerAndUser() {
        viewModelScope.launch {
            val serverId = appPreferences.getValue(appPreferences.currentServer)
            serverId?.let { id ->
                database.getServerWithAddressAndUser(id)?.let { data ->
                    _uiState.emit(UiState.Normal(data.server, data.user))
                }
            }
        }
    }

    fun acknowledgeSessionRevoked() {
        viewModelScope.launch {
            val serverId = appPreferences.getValue(appPreferences.currentServer)
            if (serverId != null) {
                val data = database.getServerWithAddressAndUser(serverId)
                val server = data?.server
                val user = data?.user
                if (server != null) {
                    database.updateServer(server.copy(currentUserId = null))
                }
                if (user != null) {
                    database.deleteUser(user.id)
                }
            }
            jellyfinApi.apply {
                api.update(accessToken = null)
                userId = null
            }
            sessionEventManager.stopListening()
            bootstrappedSessionKey = null
            _state.update { it.copy(sessionRevokedMessage = null) }
        }
    }

    private fun observeSessionRevocation() {
        viewModelScope.launch {
            sessionEventManager.events.collect { event ->
                if (event is SessionAlertEvent.SessionRevoked) {
                    _state.update { it.copy(sessionRevokedMessage = event.reason) }
                }
            }
        }
    }

    private fun ensureGlobalSessionActive(serverId: String?) {
        val currentToken = jellyfinApi.api.accessToken
        val key = "$serverId:${currentToken.orEmpty()}"
        if (serverId == null || currentToken.isNullOrBlank()) return
        if (bootstrappedSessionKey == key) return
        bootstrappedSessionKey = key
        sessionEventManager.startListening()
        viewModelScope.launch { runCatching { repositoryProvider.get().postCapabilities() } }
    }

    /**
     * Continuously derives [MainState] from the underlying preferences and database, so the state
     * (and anything derived from it, e.g. the setup/main navigation condition) reacts to changes as
     * soon as they happen.
     */
    private fun check() {
        viewModelScope.launch {
            _state.emit(MainState(isLoading = true))

            val setupFlags =
                appPreferences.observe(appPreferences.currentServer).flatMapLatest { serverId ->
                    if (serverId == null) {
                        database.observeServersCount().map { serverCount ->
                            SetupFlags(
                                serverId = null,
                                hasServers = serverCount > 0,
                                hasCurrentServer = false,
                                hasCurrentUser = false,
                            )
                        }
                    } else {
                        combine(
                            database.observeServersCount(),
                            database.observeServer(serverId),
                            database.observeServerCurrentUserId(serverId),
                        ) { serverCount, server, currentUserId ->
                            SetupFlags(
                                serverId = serverId,
                                hasServers = serverCount > 0,
                                hasCurrentServer = server != null,
                                hasCurrentUser = currentUserId != null,
                            )
                        }
                    }
                }

            combine(
                    setupFlags,
                    appPreferences.observe(appPreferences.theme),
                    appPreferences.observe(appPreferences.accentColor),
                    appPreferences.observe(appPreferences.dynamicColors),
                    appPreferences.observe(appPreferences.offlineMode),
                ) { flags, theme, accentColor, dynamicColors, offlineMode ->
                    if (flags.hasCurrentServer && flags.hasCurrentUser && !offlineMode) {
                        ensureGlobalSessionActive(flags.serverId)
                    } else {
                        bootstrappedSessionKey = null
                        sessionEventManager.stopListening()
                    }
                    MainState(
                        isLoading = false,
                        theme = theme,
                        accentColor = accentColor,
                        isDynamicColors = dynamicColors,
                        hasServers = flags.hasServers,
                        hasCurrentServer = flags.hasCurrentServer,
                        hasCurrentUser = flags.hasCurrentUser,
                        isOfflineMode = offlineMode,
                        sessionRevokedMessage = _state.value.sessionRevokedMessage,
                    )
                }
                .collect { mainState -> _state.emit(mainState) }
        }
    }

    private data class SetupFlags(
        val serverId: String?,
        val hasServers: Boolean,
        val hasCurrentServer: Boolean,
        val hasCurrentUser: Boolean,
    )
}

data class MainState(
    val isLoading: Boolean = true,
    val theme: String = "system",
    val accentColor: String = "jellyfin",
    val isDynamicColors: Boolean = true,
    val hasServers: Boolean = false,
    val hasCurrentServer: Boolean = false,
    val hasCurrentUser: Boolean = false,
    val isOfflineMode: Boolean = false,
    val sessionRevokedMessage: String? = null,
)

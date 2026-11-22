package dev.jdtech.jellyfin.session

import dev.jdtech.jellyfin.api.JellyfinApi
import java.util.UUID
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.GeneralCommandMessage
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.OutboundWebSocketMessage
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.PlaystateMessage
import org.jellyfin.sdk.model.api.SendCommandType
import org.jellyfin.sdk.model.api.SyncPlayCommandMessage
import org.jellyfin.sdk.model.api.SyncPlayGroupUpdateMessage
import timber.log.Timber

sealed interface SyncPlayEvent {
    data class Command(
        val command: SendCommandType,
        val positionTicks: Long?,
    ) : SyncPlayEvent

    data class GroupUpdated(
        val groupId: UUID,
    ) : SyncPlayEvent
}

sealed interface SessionAlertEvent {
    data class DisplayMessage(
        val header: String,
        val text: String,
        val timeoutMs: Long? = null,
        val isConcurrentStreamBlock: Boolean = false,
    ) : SessionAlertEvent

    data object ForceStopPlayback : SessionAlertEvent

    data class SessionRevoked(val reason: String) : SessionAlertEvent
}

class SessionEventManager(private val jellyfinApi: JellyfinApi) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<SessionAlertEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<SessionAlertEvent> = _events.asSharedFlow()

    private val _syncPlayEvents = MutableSharedFlow<SyncPlayEvent>(extraBufferCapacity = 32)
    val syncPlayEvents: SharedFlow<SyncPlayEvent> = _syncPlayEvents.asSharedFlow()

    private var listenerJob: Job? = null

    fun startListening() {
        if (listenerJob?.isActive == true) return
        if (jellyfinApi.api.accessToken.isNullOrBlank()) return

        listenerJob = scope.launch {
            var backoffMs = INITIAL_BACKOFF_MS
            while (isActive) {
                if (jellyfinApi.api.accessToken.isNullOrBlank()) {
                    break
                }
                try {
                    jellyfinApi.api.webSocket.subscribeAll().collect { message ->
                        backoffMs = INITIAL_BACKOFF_MS
                        handleSocketMessage(message)?.let { event -> _events.tryEmit(event) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isUnauthorizedError(e)) {
                        Timber.w(e, "WebSocket received 401/Unauthorized; revoking session")
                        emitSessionRevoked(
                            "Tu sesión fue cerrada o tu acceso fue restringido por un administrador."
                        )
                        break
                    }
                    Timber.w(e, "WebSocket disconnected, retrying in %d ms", backoffMs)
                }
                delay(backoffMs)
                backoffMs = min(backoffMs * 2, MAX_BACKOFF_MS)
            }
        }
    }

    fun emitSessionRevoked(reason: String) {
        _events.tryEmit(SessionAlertEvent.SessionRevoked(reason))
    }

    private fun isUnauthorizedError(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            val msg = current.message.orEmpty()
            if (
                msg.contains("401") ||
                    msg.contains("Unauthorized", ignoreCase = true) ||
                    msg.contains("Invalid token", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    fun stopListening() {
        listenerJob?.cancel()
        listenerJob = null
    }

    internal fun handleSocketMessage(message: OutboundWebSocketMessage): SessionAlertEvent? {
        return when (message) {
            is GeneralCommandMessage -> {
                val data = message.data ?: return null
                if (data.name != GeneralCommandType.DISPLAY_MESSAGE) return null
                val args = data.arguments
                val header = args["Header"].orEmpty().ifBlank { "Jellyx.tv" }
                val text = args["Text"].orEmpty()
                val timeoutMs = args["TimeoutMs"]?.toLongOrNull()
                val isConcurrentBlock = CONCURRENT_KEYWORDS.any { keyword ->
                    header.contains(keyword, ignoreCase = true) ||
                        text.contains(keyword, ignoreCase = true)
                }
                SessionAlertEvent.DisplayMessage(
                    header = header,
                    text = text,
                    timeoutMs = timeoutMs,
                    isConcurrentStreamBlock = isConcurrentBlock,
                )
            }
            is PlaystateMessage -> {
                if (message.data?.command == PlaystateCommand.STOP) {
                    SessionAlertEvent.ForceStopPlayback
                } else {
                    null
                }
            }
            is SyncPlayCommandMessage -> {
                message.data?.let { cmd ->
                    _syncPlayEvents.tryEmit(
                        SyncPlayEvent.Command(
                            command = cmd.command,
                            positionTicks = cmd.positionTicks,
                        )
                    )
                }
                null
            }
            is SyncPlayGroupUpdateMessage -> {
                message.data?.groupId?.let { groupId ->
                    _syncPlayEvents.tryEmit(SyncPlayEvent.GroupUpdated(groupId = groupId))
                }
                null
            }
            else -> null
        }
    }

    companion object {
        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L
        private val CONCURRENT_KEYWORDS =
            listOf(
                "pantalla",
                "simult",
                "concurrent",
                "screen",
                "limit",
                "límite",
                "dispositivo",
                "strike",
                "exceso",
                "sesión",
                "sesion",
                "bloque",
                "advertencia",
                "warning",
                "cuenta",
            )
    }
}

package dev.jdtech.jellyfin.session

import dev.jdtech.jellyfin.api.JellyfinApi
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.GeneralCommand
import org.jellyfin.sdk.model.api.GeneralCommandMessage
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.PlaystateMessage
import org.jellyfin.sdk.model.api.PlaystateRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEventManagerTest {

    private val jellyfinApi = mockk<JellyfinApi>(relaxed = true)
    private val manager = SessionEventManager(jellyfinApi)

    @Test
    fun `GeneralCommandMessage DISPLAY_MESSAGE parses header, text, and concurrent flag`() {
        val msg =
            GeneralCommandMessage(
                data =
                    GeneralCommand(
                        name = GeneralCommandType.DISPLAY_MESSAGE,
                        controllingUserId = UUID.randomUUID(),
                        arguments =
                            mapOf(
                                "Header" to "Límite de pantallas",
                                "Text" to "Solo 1 pantalla simultánea permitida.",
                                "TimeoutMs" to "8000",
                            ),
                    ),
                messageId = UUID.randomUUID(),
            )

        val event = manager.handleSocketMessage(msg)

        assertTrue(event is SessionAlertEvent.DisplayMessage)
        val display = event as SessionAlertEvent.DisplayMessage
        assertEquals("Límite de pantallas", display.header)
        assertEquals("Solo 1 pantalla simultánea permitida.", display.text)
        assertEquals(8000L, display.timeoutMs)
        assertTrue(display.isConcurrentStreamBlock)
    }

    @Test
    fun `PlaystateMessage STOP maps to ForceStopPlayback`() {
        val msg =
            PlaystateMessage(
                data =
                    PlaystateRequest(
                        command = PlaystateCommand.STOP,
                        seekPositionTicks = null,
                        controllingUserId = null,
                    ),
                messageId = UUID.randomUUID(),
            )

        val event = manager.handleSocketMessage(msg)

        assertEquals(SessionAlertEvent.ForceStopPlayback, event)
    }

    @Test
    fun `GeneralCommandMessage DISPLAY_MESSAGE detects strike and exceso keywords as concurrent block`() {
        val msg =
            GeneralCommandMessage(
                data =
                    GeneralCommand(
                        name = GeneralCommandType.DISPLAY_MESSAGE,
                        controllingUserId = UUID.randomUUID(),
                        arguments =
                            mapOf(
                                "Header" to "Advertencia · Strike 1",
                                "Text" to "Exceso de reproducciones activas en tu cuenta.",
                            ),
                    ),
                messageId = UUID.randomUUID(),
            )

        val event = manager.handleSocketMessage(msg) as SessionAlertEvent.DisplayMessage
        assertTrue(event.isConcurrentStreamBlock)
    }

    @Test
    fun `emitSessionRevoked delivers SessionRevoked event`() = runTest {
        val deferred = async(start = CoroutineStart.UNDISPATCHED) { manager.events.first() }
        manager.emitSessionRevoked("Token inválido")
        val first = deferred.await()
        assertTrue(first is SessionAlertEvent.SessionRevoked)
        assertEquals("Token inválido", (first as SessionAlertEvent.SessionRevoked).reason)
    }
}

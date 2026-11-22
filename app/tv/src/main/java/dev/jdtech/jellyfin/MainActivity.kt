package dev.jdtech.jellyfin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.core.presentation.components.AppUpdateDialog
import dev.jdtech.jellyfin.core.presentation.components.ConcurrentScreenAlertDialog
import dev.jdtech.jellyfin.core.presentation.components.JellyxLoadingLogo
import dev.jdtech.jellyfin.core.presentation.components.JellyxStartupSplash
import dev.jdtech.jellyfin.presentation.theme.JellyxTheme
import dev.jdtech.jellyfin.setup.domain.SetupRepository
import dev.jdtech.jellyfin.update.AppUpdateManager
import dev.jdtech.jellyfin.viewmodels.MainViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var setupRepository: SetupRepository
    @Inject lateinit var appUpdateManager: AppUpdateManager

    private val viewModel: MainViewModel by viewModels()
    private val isSeedReady = mutableStateOf(false)
    private val pendingDeepLinkRoute = mutableStateOf<NavKey?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)

        lifecycleScope.launch {
            val seeded = setupRepository.seedDefaultServer()
            if (seeded) {
                withTimeoutOrNull(2_000) {
                    viewModel.state.first { !it.isLoading && it.hasServers && it.hasCurrentServer }
                }
            }
            isSeedReady.value = true
        }
        lifecycleScope.launch(Dispatchers.IO) {
            appUpdateManager.checkForUpdates(force = false)
        }

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val availableUpdate by appUpdateManager.availableUpdate.collectAsStateWithLifecycle()
            val updateProgress by appUpdateManager.downloadProgress.collectAsStateWithLifecycle()
            val updateError by appUpdateManager.updateError.collectAsStateWithLifecycle()
            var showStartupSplash by rememberSaveable { mutableStateOf(true) }
            val darkThemeOverride =
                when (state.theme) {
                    "light" -> false
                    "dark" -> true
                    else -> null
                }

            JellyxTheme(darkTheme = darkThemeOverride, accentColor = state.accentColor) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (!state.isLoading && isSeedReady.value) {
                        NavigationRoot(
                            hasServers = state.hasServers,
                            hasCurrentServer = state.hasCurrentServer,
                            hasCurrentUser = state.hasCurrentUser,
                            pendingDeepLinkRoute = pendingDeepLinkRoute.value,
                            onDeepLinkConsumed = { pendingDeepLinkRoute.value = null },
                        )
                    }
                    if (!showStartupSplash && (state.isLoading || !isSeedReady.value)) {
                        JellyxLoadingLogo(modifier = Modifier.align(Alignment.Center))
                    }
                    if (showStartupSplash) {
                        JellyxStartupSplash(onFinished = { showStartupSplash = false })
                    }
                    state.sessionRevokedMessage?.let { revokedMsg ->
                        ConcurrentScreenAlertDialog(
                            header = "Sesión restringida",
                            message = revokedMsg,
                            isBlocking = true,
                            confirmLabel = "Ir al inicio de sesión",
                            onDismiss = { viewModel.acknowledgeSessionRevoked() },
                        )
                    }
                    availableUpdate?.let { update ->
                        if (state.sessionRevokedMessage == null) {
                            AppUpdateDialog(
                                updateInfo = update,
                                downloadProgress = updateProgress,
                                errorMessage = updateError,
                                onUpdateClick = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        appUpdateManager.downloadAndInstall(update)
                                    }
                                },
                                onDismiss = { appUpdateManager.dismissUpdate(update) },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "jellyx" || data.host != "item") return
        val segments = data.pathSegments ?: return
        val kind = segments.getOrNull(0) ?: return
        val itemId = segments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return
        pendingDeepLinkRoute.value =
            when (kind) {
                "movie" -> MovieRoute(itemId)
                "show" -> ShowRoute(itemId)
                else -> null
            }
    }
}

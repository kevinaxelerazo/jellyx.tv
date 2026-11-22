package dev.jdtech.jellyfin.presentation.settings

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.settings.components.SettingsGroupCard
import dev.jdtech.jellyfin.presentation.theme.JellyxTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.settings.R as SettingsR
import dev.jdtech.jellyfin.settings.presentation.enums.DeviceType
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceCategory
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceGroup
import dev.jdtech.jellyfin.settings.presentation.settings.SettingsAction
import dev.jdtech.jellyfin.settings.presentation.settings.SettingsEvent
import dev.jdtech.jellyfin.settings.presentation.settings.SettingsState
import dev.jdtech.jellyfin.settings.presentation.settings.SettingsViewModel
import dev.jdtech.jellyfin.utils.ObserveAsEvents
import dev.jdtech.jellyfin.utils.restart
import timber.log.Timber

@Composable
fun SettingsScreen(
    navigateToSubSettings: (indexes: List<Int>) -> Unit,
    navigateToServers: () -> Unit,
    navigateToUsers: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.loadPreferences(emptyList(), DeviceType.TV) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SettingsEvent.NavigateToSettings -> navigateToSubSettings(event.indexes)
            is SettingsEvent.NavigateToSettingsFileEdit -> {}
            is SettingsEvent.NavigateToUsers -> navigateToUsers()
            is SettingsEvent.NavigateToServers -> navigateToServers()
            is SettingsEvent.NavigateToAbout -> Unit
            is SettingsEvent.UpdateTheme -> Unit
            is SettingsEvent.LaunchIntent -> {
                try {
                    context.startActivity(event.intent)
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
            is SettingsEvent.RestartActivity -> {
                try {
                    (context as Activity).restart()
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
        }
    }

    SettingsScreenLayout(
        state = state,
        onAction = { action ->
            when (action) {
                is SettingsAction.OnUpdate -> {
                    viewModel.onAction(action)
                    viewModel.loadPreferences(emptyList(), DeviceType.TV)
                }
                else -> Unit
            }
        },
    )
}

@Composable
private fun SettingsScreenLayout(state: SettingsState, onAction: (SettingsAction) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val launcherAlias = remember(context) {
        ComponentName(context, "dev.jdtech.jellyfin.HomeLauncherAlias")
    }
    var isHomeLauncherEnabled by remember {
        mutableStateOf(
            runCatching {
                    context.packageManager.getComponentEnabledSetting(launcherAlias) ==
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                }
                .getOrDefault(false)
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
        contentPadding =
            PaddingValues(
                horizontal = MaterialTheme.spacings.default * 2,
                vertical = MaterialTheme.spacings.large,
            ),
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester),
    ) {
        item(span = { GridItemSpan(this.maxLineSpan) }) {
            Text(
                text = stringResource(id = SettingsR.string.title_settings),
                style = MaterialTheme.typography.displayMedium,
            )
        }
        items(state.preferenceGroups) { group ->
            SettingsGroupCard(group = group, onAction = onAction)
        }
        item(span = { GridItemSpan(this.maxLineSpan) }) {
            Surface(
                onClick = {
                    val nextEnabled = !isHomeLauncherEnabled
                    runCatching {
                        context.packageManager.setComponentEnabledSetting(
                            launcherAlias,
                            if (nextEnabled) {
                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            } else {
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            },
                            PackageManager.DONT_KILL_APP,
                        )
                        isHomeLauncherEnabled = nextEnabled
                        if (nextEnabled) {
                            val homeSettingsIntent =
                                Intent(Settings.ACTION_HOME_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            runCatching { context.startActivity(homeSettingsIntent) }.onFailure {
                                val chooserIntent =
                                    Intent(Intent.ACTION_MAIN).apply {
                                        addCategory(Intent.CATEGORY_HOME)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                runCatching { context.startActivity(chooserIntent) }
                            }
                        }
                    }
                },
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                    ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Icon(
                        painter = painterResource(id = CoreR.drawable.ic_home),
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "TV Home Launcher (Pantalla de inicio)",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text =
                                if (isHomeLauncherEnabled) {
                                    "Activado • Toca para desactivar y volver al Home del sistema"
                                } else {
                                    "Desactivado • Toca para habilitar Jellyx.tv como pantalla de inicio"
                                },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(true) { focusRequester.requestFocus() }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun SettingsScreenLayoutPreview() {
    JellyxTheme {
        SettingsScreenLayout(
            state =
                SettingsState(
                    preferenceGroups =
                        listOf(
                            PreferenceGroup(
                                nameStringResource = null,
                                preferences =
                                    listOf(
                                        PreferenceCategory(
                                            nameStringResource =
                                                SettingsR.string.settings_category_language,
                                            iconDrawableId = SettingsR.drawable.ic_languages,
                                        )
                                    ),
                            ),
                            PreferenceGroup(
                                nameStringResource = null,
                                preferences =
                                    listOf(
                                        PreferenceCategory(
                                            nameStringResource =
                                                SettingsR.string.settings_category_appearance,
                                            iconDrawableId = SettingsR.drawable.ic_palette,
                                        )
                                    ),
                            ),
                        )
                ),
            onAction = {},
        )
    }
}

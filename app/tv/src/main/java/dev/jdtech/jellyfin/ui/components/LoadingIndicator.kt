package dev.jdtech.jellyfin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.presentation.components.JellyxLoadingLogo

@Composable
fun LoadingIndicator() {
    JellyxLoadingLogo(size = 32.dp)
}

package dev.jdtech.jellyfin.presentation.setup.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.presentation.components.JellyxLoadingLogo
import dev.jdtech.jellyfin.presentation.theme.JellyxTheme

@Composable
fun LoadingButton(
    text: String,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box {
        if (isLoading) {
            JellyxLoadingLogo(
                size = 22.dp,
                modifier = Modifier.align(Alignment.CenterStart).offset(x = 8.dp),
            )
        }
        Button(onClick = onClick, enabled = !isLoading, modifier = modifier) { Text(text = text) }
    }
}

@Composable
@Preview
private fun LoadingButtonPreview() {
    JellyxTheme { LoadingButton(text = "Connect", onClick = {}, isLoading = true) }
}

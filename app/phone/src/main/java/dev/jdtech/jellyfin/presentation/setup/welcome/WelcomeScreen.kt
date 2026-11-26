package dev.jdtech.jellyfin.presentation.setup.welcome

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.presentation.components.JellyxWelcomeBanner
import dev.jdtech.jellyfin.presentation.setup.components.RootLayout
import dev.jdtech.jellyfin.presentation.theme.JellyxTheme
import dev.jdtech.jellyfin.setup.R as SetupR

@Composable
fun WelcomeScreen(onContinueClick: () -> Unit) {
    WelcomeScreenLayout(onContinueClick = onContinueClick)
}

@Composable
private fun WelcomeScreenLayout(onContinueClick: () -> Unit) {
    RootLayout(padding = PaddingValues(horizontal = 24.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center).verticalScroll(rememberScrollState()),
        ) {
            JellyxWelcomeBanner(compact = true)
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(SetupR.string.welcome),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(SetupR.string.welcome_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onContinueClick,
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            ) {
                Text(text = stringResource(SetupR.string.welcome_btn_continue))
            }
        }
    }
}

@PreviewScreenSizes
@Composable
private fun WelcomeScreenLayoutPreview() {
    JellyxTheme { WelcomeScreenLayout(onContinueClick = {}) }
}

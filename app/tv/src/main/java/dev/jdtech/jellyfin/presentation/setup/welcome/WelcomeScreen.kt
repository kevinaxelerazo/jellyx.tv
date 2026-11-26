package dev.jdtech.jellyfin.presentation.setup.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.presentation.components.JellyxWelcomeBanner
import dev.jdtech.jellyfin.presentation.theme.JellyxTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.setup.R as SetupR

@Composable
fun WelcomeScreen(onContinueClick: () -> Unit) {
    WelcomeScreenLayout(onContinueClick = onContinueClick)
}

@Composable
private fun WelcomeScreenLayout(onContinueClick: () -> Unit) {
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 48.dp).widthIn(max = 680.dp),
        ) {
            JellyxWelcomeBanner()
            Spacer(modifier = Modifier.height(MaterialTheme.spacings.large))
            Text(
                text = stringResource(SetupR.string.welcome),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
            Text(
                text = stringResource(SetupR.string.welcome_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacings.large))
            Button(
                onClick = onContinueClick,
                modifier = Modifier.focusRequester(focusRequester),
            ) {
                Text(text = stringResource(SetupR.string.welcome_btn_continue))
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun WelcomeScreenLayoutPreview() {
    JellyxTheme { WelcomeScreenLayout(onContinueClick = {}) }
}

package dev.jdtech.jellyfin.core.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jdtech.jellyfin.core.R as CoreR

/**
 * High-contrast JellyX.tv brand banner shared across Phone, Tablet, and TV welcome screens. Uses
 * Material 3 theme tokens so it remains legible on both light and OLED dark themes.
 */
@Composable
fun JellyxWelcomeBanner(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val logoSize = if (compact) 64.dp else 76.dp
    val horizontalSpacing = if (compact) 12.dp else 16.dp
    val wordmarkSize = if (compact) 30.sp else 36.sp
    val wordmarkLineHeight = if (compact) 34.sp else 40.sp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
    ) {
        Image(
            painter = painterResource(id = CoreR.drawable.ic_logo),
            contentDescription = "JellyX.tv",
            modifier = Modifier.size(logoSize),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text =
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground)) {
                            append("JellyX")
                        }
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                            append(".tv")
                        }
                    },
                fontSize = wordmarkSize,
                lineHeight = wordmarkLineHeight,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
            )
            Text(
                text = "Tu cine privado y TV en vivo",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style =
                    if (compact) {
                        MaterialTheme.typography.bodySmall
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

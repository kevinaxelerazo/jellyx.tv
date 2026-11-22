package dev.jdtech.jellyfin.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import dev.jdtech.jellyfin.core.presentation.theme.Spacings
import dev.jdtech.jellyfin.core.presentation.theme.accentPaletteFor

@Composable
fun JellyxTheme(
    darkTheme: Boolean? = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    accentColor: String = "jellyfin",
    content: @Composable () -> Unit,
) {
    val isDark = darkTheme ?: isSystemInDarkTheme()
    val accent = accentPaletteFor(accentColor)
    val baseScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            isDark -> darkScheme
            else -> lightScheme
        }
    val colorScheme =
        if (accent != null) {
            baseScheme.copy(
                primary = if (isDark) accent.primaryDark else accent.primaryLight,
                onPrimary = if (isDark) accent.onPrimaryDark else accent.onPrimaryLight,
            )
        } else {
            baseScheme
        }

    MaterialTheme(colorScheme = colorScheme, shapes = shapes) {
        CompositionLocalProvider(
            LocalContentColor provides contentColorFor(MaterialTheme.colorScheme.background),
            LocalSpacings provides Spacings,
        ) {
            content()
        }
    }
}

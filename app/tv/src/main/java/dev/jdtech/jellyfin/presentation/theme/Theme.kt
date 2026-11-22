package dev.jdtech.jellyfin.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.LocalContentColor as LocalContentColorTv
import androidx.tv.material3.MaterialTheme as MaterialThemeTv
import androidx.tv.material3.contentColorFor as contentColorForTv
import dev.jdtech.jellyfin.core.presentation.theme.Spacings
import dev.jdtech.jellyfin.core.presentation.theme.accentPaletteFor

@Composable
fun JellyxTheme(
    darkTheme: Boolean? = null,
    accentColor: String = "jellyfin",
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = darkTheme ?: isSystemInDarkTheme()
    val accent = accentPaletteFor(accentColor)
    val baseScheme = if (isDark) darkScheme else lightScheme
    val baseSchemeTv = if (isDark) darkSchemeTv else lightSchemeTv

    val colorScheme =
        if (accent != null) {
            baseScheme.copy(
                primary = if (isDark) accent.primaryDark else accent.primaryLight,
                onPrimary = if (isDark) accent.onPrimaryDark else accent.onPrimaryLight,
            )
        } else {
            baseScheme
        }

    val colorSchemeTv =
        if (accent != null) {
            baseSchemeTv.copy(
                primary = if (isDark) accent.primaryDark else accent.primaryLight,
                onPrimary = if (isDark) accent.onPrimaryDark else accent.onPrimaryLight,
            )
        } else {
            baseSchemeTv
        }

    val bgGradient =
        if (isDark) {
            listOf(Color.Black, Color(0xFF001721))
        } else {
            listOf(Color(0xFFF6F9FC), Color(0xFFE7EFF7))
        }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, shapes = shapes) {
        MaterialThemeTv(
            colorScheme = colorSchemeTv,
            typography = TypographyTv,
            shapes = shapesTv,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides contentColorFor(MaterialTheme.colorScheme.background),
                LocalContentColorTv provides
                    contentColorForTv(MaterialThemeTv.colorScheme.background),
                LocalSpacings provides Spacings,
            ) {
                Box(modifier = Modifier.background(Brush.linearGradient(bgGradient))) {
                    content()
                }
            }
        }
    }
}

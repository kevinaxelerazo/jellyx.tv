package dev.jdtech.jellyfin.core.presentation.theme

import androidx.compose.ui.graphics.Color

data class AccentPalette(
    val primaryDark: Color,
    val onPrimaryDark: Color,
    val primaryLight: Color,
    val onPrimaryLight: Color,
)

fun accentPaletteFor(key: String?): AccentPalette? =
    when (key) {
        "netflix" ->
            AccentPalette(
                primaryDark = Color(0xFFE50914),
                onPrimaryDark = Color.White,
                primaryLight = Color(0xFFD30813),
                onPrimaryLight = Color.White,
            )
        "emerald" ->
            AccentPalette(
                primaryDark = Color(0xFF10B981),
                onPrimaryDark = Color(0xFF002116),
                primaryLight = Color(0xFF059669),
                onPrimaryLight = Color.White,
            )
        "purple" ->
            AccentPalette(
                primaryDark = Color(0xFFAA5CC3),
                onPrimaryDark = Color.White,
                primaryLight = Color(0xFF8B3FA8),
                onPrimaryLight = Color.White,
            )
        "amber" ->
            AccentPalette(
                primaryDark = Color(0xFFF59E0B),
                onPrimaryDark = Color(0xFF281800),
                primaryLight = Color(0xFFD97706),
                onPrimaryLight = Color.White,
            )
        else -> null
    }


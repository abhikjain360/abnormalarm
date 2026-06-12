package com.abhikjain360.abnormalarm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Catppuccin Mocha mapped onto Material 3 dark roles (DESIGN.md §9). No light scheme, no dynamic
// color — dark only by design. Only well-established M3 roles are set; the rest derive sensibly.
private val MochaColorScheme = darkColorScheme(
    primary = Mauve,
    onPrimary = Crust,
    primaryContainer = Surface1,
    onPrimaryContainer = Text,
    secondary = Lavender,
    onSecondary = Crust,
    secondaryContainer = Surface1,
    onSecondaryContainer = Text,
    tertiary = Teal,
    onTertiary = Crust,
    background = Base,
    onBackground = Text,
    surface = Mantle,
    onSurface = Text,
    surfaceVariant = Surface0,
    onSurfaceVariant = Subtext0,
    error = Red,
    onError = Crust,
    outline = Overlay0,
    outlineVariant = Surface1,
)

@Composable
fun AbnormalarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MochaColorScheme,
        typography = Typography,
        content = content,
    )
}

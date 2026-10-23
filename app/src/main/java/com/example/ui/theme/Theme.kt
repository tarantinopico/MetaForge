package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.metaforge.repository.ThemeMode

private val AmoledColorScheme = darkColorScheme(
    primary = TealPrimary,
    onPrimary = TealOnPrimary,
    primaryContainer = TealPrimaryContainer,
    onPrimaryContainer = TealOnPrimaryContainer,
    secondary = IceSecondary,
    onSecondary = IceOnSecondary,
    secondaryContainer = IceSecondaryContainer,
    background = AmoledBackground,
    onBackground = Color(0xFFF0F4F8),
    surface = AmoledSurface,
    onSurface = Color(0xFFF0F4F8),
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = Color(0xFFC0CAD4),
    outline = Color(0xFF283644),
    error = StatusRemoved,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = TealPrimary,
    onPrimary = TealOnPrimary,
    primaryContainer = TealPrimaryContainer,
    onPrimaryContainer = TealOnPrimaryContainer,
    secondary = IceSecondary,
    onSecondary = IceOnSecondary,
    background = DarkBackground,
    onBackground = Color(0xFFE8EEF4),
    surface = DarkSurface,
    onSurface = Color(0xFFE8EEF4),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFBCC6D0),
    outline = Color(0xFF333E4A),
    error = StatusRemoved,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF007563),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF80FFE8),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF00687A),
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF161C22),
    surface = LightSurface,
    onSurface = Color(0xFF161C22),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF424C56),
    outline = Color(0xFFB4BFCB),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

@Composable
fun MetaForgeTheme(
    themeMode: ThemeMode = ThemeMode.AMOLED,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val colorScheme = when (themeMode) {
        ThemeMode.AMOLED -> AmoledColorScheme
        ThemeMode.DARK -> DarkColorScheme
        ThemeMode.LIGHT -> LightColorScheme
        ThemeMode.SYSTEM -> if (systemInDark) DarkColorScheme else LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

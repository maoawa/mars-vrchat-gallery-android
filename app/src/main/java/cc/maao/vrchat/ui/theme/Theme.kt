package cc.maao.vrchat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GalleryMint,
    onPrimary = GalleryDarkBackground,
    secondary = GalleryViolet,
    onSecondary = GalleryDarkBackground,
    tertiary = GalleryGold,
    onTertiary = GalleryDarkBackground,
    background = GalleryDarkBackground,
    onBackground = GalleryDarkText,
    surface = GalleryDarkSurface,
    onSurface = GalleryDarkText,
    surfaceVariant = GalleryDarkSurfaceVariant,
    onSurfaceVariant = GalleryDarkMuted,
    secondaryContainer = GalleryDarkSurface,
    onSecondaryContainer = GalleryDarkText,
    tertiaryContainer = GalleryDarkSurface,
    onTertiaryContainer = GalleryDarkText,
    outline = GalleryDarkMuted,
    scrim = GalleryDarkBackground,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2F725D),
    onPrimary = GalleryLightSurface,
    secondary = Color(0xFF6B45A8),
    onSecondary = GalleryLightSurface,
    tertiary = Color(0xFF9A651F),
    onTertiary = GalleryLightSurface,
    background = GalleryLightBackground,
    onBackground = GalleryLightText,
    surface = GalleryLightSurface,
    onSurface = GalleryLightText,
    surfaceVariant = GalleryLightSurfaceVariant,
    onSurfaceVariant = GalleryLightMuted,
    secondaryContainer = GalleryLightSurface,
    onSecondaryContainer = GalleryLightText,
    tertiaryContainer = GalleryLightSurface,
    onTertiaryContainer = GalleryLightText,
    outline = GalleryLightMuted,
    scrim = GalleryDarkBackground,
)

@Composable
fun MarsVRChatGalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

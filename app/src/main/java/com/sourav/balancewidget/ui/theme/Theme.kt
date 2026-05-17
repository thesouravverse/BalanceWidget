package com.sourav.balancewidget.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// CRED-inspired palette: pitch-black canvas, near-black surfaces, white text,
// magenta/purple hero gradient, gold (CRED Coin) accent.
object CredPalette {
    val Background = Color(0xFF0A0A0A)
    val Surface = Color(0xFF141416)
    val SurfaceVariant = Color(0xFF1C1C1F)
    val Border = Color(0x14FFFFFF)        // white 8%
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0x99FFFFFF) // white 60%
    val TextMuted = Color(0x66FFFFFF)     // white 40%
    val Gold = Color(0xFFE9B949)          // CRED coin-ish
    val GoldDeep = Color(0xFFB8862E)
    val HeroStart = Color(0xFF4C1D95)     // deep purple
    val HeroEnd = Color(0xFFC026D3)       // magenta
    val Success = Color(0xFF10B981)
    val Danger = Color(0xFFEF4444)
}

private val CredDark = darkColorScheme(
    primary = CredPalette.Gold,
    onPrimary = Color.Black,
    primaryContainer = CredPalette.HeroStart,
    onPrimaryContainer = CredPalette.TextPrimary,
    secondary = CredPalette.HeroEnd,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2A1830),
    onSecondaryContainer = CredPalette.TextPrimary,
    background = CredPalette.Background,
    onBackground = CredPalette.TextPrimary,
    surface = CredPalette.Surface,
    onSurface = CredPalette.TextPrimary,
    surfaceVariant = CredPalette.SurfaceVariant,
    onSurfaceVariant = CredPalette.TextSecondary,
    outline = CredPalette.Border,
    outlineVariant = CredPalette.Border,
    error = CredPalette.Danger,
    onError = Color.White
)

@Composable
fun BalanceWidgetTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    // CRED look is always dark — ignore system/dynamic color.
    MaterialTheme(colorScheme = CredDark, content = content)
}

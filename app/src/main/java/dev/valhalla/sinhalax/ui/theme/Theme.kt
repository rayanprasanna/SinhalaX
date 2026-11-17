package dev.valhalla.sinhalax.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF0B1F4B)
val NavyDeep = Color(0xFF07142F)
val NavySurface = Color(0xFF12295C)
val NavyHigh = Color(0xFF1A3570)
val Gold = Color(0xFFF4B91F)
val GoldSoft = Color(0xFFFFE08A)
val Ink = Color(0xFFE8ECF5)
val InkMuted = Color(0xFFAAB6D0)
val Success = Color(0xFF4ADE80)

private val BrandScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Navy,
    primaryContainer = Color(0xFF3A2E07),
    onPrimaryContainer = GoldSoft,
    secondary = Color(0xFF8FB4FF),
    onSecondary = Navy,
    background = NavyDeep,
    onBackground = Ink,
    surface = NavyDeep,
    onSurface = Ink,
    surfaceVariant = NavySurface,
    onSurfaceVariant = InkMuted,
    surfaceContainer = Color(0xFF0D2152),
    surfaceContainerHigh = NavySurface,
    surfaceContainerHighest = NavyHigh,
    outline = Color(0xFF3A4F80),
    outlineVariant = Color(0xFF243A6B),
)

@Composable
fun SinhalaXTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BrandScheme, content = content)
}

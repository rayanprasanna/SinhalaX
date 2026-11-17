package dev.valhalla.sinhalax.keyboard

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class KeyboardTheme(
    val id: String,
    val name: String,
    /** One colour = solid background, two or more = vertical gradient. */
    val background: List<Color>,
    val key: Color,
    val keyPressed: Color,
    val specialKey: Color,
    val text: Color,
    val hint: Color,
    val accent: Color,
    val onAccent: Color,
    val toolbarText: Color,
    val keyBorder: Color? = null,
    /** Animated rainbow key borders. */
    val rgbBorders: Boolean = false,
    val keyRadius: Dp = 8.dp,
)

object KeyboardThemes {

    val NavyGold = KeyboardTheme(
        id = "navy_gold", name = "Navy Gold",
        background = listOf(Color(0xFF0B1F4B), Color(0xFF07142F)),
        key = Color(0xFF16305F), keyPressed = Color(0xFF2A4C8A), specialKey = Color(0xFF0F2652),
        text = Color.White, hint = Color(0xFFF4B91F),
        accent = Color(0xFFF4B91F), onAccent = Color(0xFF0B1F4B), toolbarText = Color(0xFFE8ECF5),
    )

    val Midnight = KeyboardTheme(
        id = "midnight", name = "Midnight",
        background = listOf(Color(0xFF121418)),
        key = Color(0xFF2A2D33), keyPressed = Color(0xFF454952), specialKey = Color(0xFF1E2025),
        text = Color(0xFFF1F3F6), hint = Color(0xFF8E96A3),
        accent = Color(0xFF4FC3F7), onAccent = Color(0xFF00222E), toolbarText = Color(0xFFDDE1E7),
    )

    val Paper = KeyboardTheme(
        id = "paper", name = "Paper",
        background = listOf(Color(0xFFE9ECF1)),
        key = Color.White, keyPressed = Color(0xFFD5DAE3), specialKey = Color(0xFFCBD1DB),
        text = Color(0xFF1B1F27), hint = Color(0xFF5C6576),
        accent = Color(0xFF0B1F4B), onAccent = Color.White, toolbarText = Color(0xFF2A2F3A),
    )

    val NeonRgb = KeyboardTheme(
        id = "neon_rgb", name = "Neon RGB",
        background = listOf(Color(0xFF050608)),
        key = Color(0xFF111318), keyPressed = Color(0xFF262A33), specialKey = Color(0xFF0C0D11),
        text = Color.White, hint = Color(0xFFB0B8C8),
        accent = Color(0xFF00E5FF), onAccent = Color(0xFF001A1F), toolbarText = Color(0xFFE6E9EF),
        rgbBorders = true,
    )

    val Sunset = KeyboardTheme(
        id = "sunset", name = "Sunset",
        background = listOf(Color(0xFFFF6A3D), Color(0xFFE5326B), Color(0xFF7B2FF7)),
        key = Color(0x33FFFFFF), keyPressed = Color(0x66FFFFFF), specialKey = Color(0x22000000),
        text = Color.White, hint = Color(0xFFFFE3B3),
        accent = Color.White, onAccent = Color(0xFFE5326B), toolbarText = Color.White,
        keyRadius = 12.dp,
    )

    val Ocean = KeyboardTheme(
        id = "ocean", name = "Ocean",
        background = listOf(Color(0xFF0F766E), Color(0xFF1E3A8A)),
        key = Color(0x2EFFFFFF), keyPressed = Color(0x55FFFFFF), specialKey = Color(0x1F000000),
        text = Color.White, hint = Color(0xFFA7F3D0),
        accent = Color(0xFFA7F3D0), onAccent = Color(0xFF064E3B), toolbarText = Color.White,
        keyRadius = 12.dp,
    )

    val Tea = KeyboardTheme(
        id = "tea", name = "Ceylon Tea",
        background = listOf(Color(0xFF14361F)),
        key = Color(0xFF1E4A2C), keyPressed = Color(0xFF2F6B42), specialKey = Color(0xFF163A22),
        text = Color(0xFFF3F7EE), hint = Color(0xFFD4B26A),
        accent = Color(0xFFD4B26A), onAccent = Color(0xFF14361F), toolbarText = Color(0xFFE9F1E3),
        keyBorder = Color(0x33D4B26A),
    )

    val all: List<KeyboardTheme> = listOf(NavyGold, Midnight, Paper, NeonRgb, Sunset, Ocean, Tea)

    fun byId(id: String): KeyboardTheme = all.firstOrNull { it.id == id } ?: NavyGold
}

/**
 * Theme used on top of a downloaded background image. Keys become translucent so the image shows
 * through, and all labels use the image's key text colour (white or near-black, picked by Audio Hub).
 * The accent colour of the chosen built-in theme is kept.
 */
fun KeyboardTheme.onImage(keyText: Color, dominant: Color): KeyboardTheme {
    val lightLabels = keyText.red + keyText.green + keyText.blue > 1.5f
    return copy(
        background = listOf(dominant),
        key = if (lightLabels) Color(0x2EFFFFFF) else Color(0x99FFFFFF),
        keyPressed = if (lightLabels) Color(0x5CFFFFFF) else Color(0xD9FFFFFF),
        specialKey = if (lightLabels) Color(0x26000000) else Color(0x66FFFFFF),
        text = keyText,
        hint = keyText.copy(alpha = 0.75f),
        toolbarText = keyText,
        keyBorder = null,
    )
}

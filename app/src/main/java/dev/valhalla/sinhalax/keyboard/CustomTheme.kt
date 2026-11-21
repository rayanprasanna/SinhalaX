package dev.valhalla.sinhalax.keyboard

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/**
 * "My theme" from the DIY theme studio (Themes → DIY): background (solid or gradient), key colour,
 * key shape and outline, text and accent colours, and an optional photo with dim and blur.
 * Saved as JSON in KeyboardSettings.customTheme; used when themeId == [ID].
 * Colours are ARGB longs (0xAARRGGBB) so translucent "glass" keys work.
 */
@Immutable
data class CustomThemeSpec(
    val background: Long = 0xFF0B1F4B,
    /** Second colour for a vertical gradient, or 0 for a solid background. */
    val background2: Long = 0xFF07142F,
    val key: Long = 0xFF16305F,
    val text: Long = 0xFFFFFFFF,
    val accent: Long = 0xFFF4B91F,
    val shape: KeyShape = KeyShape.ROUNDED,
    /** Thin accent-coloured outline around keys. */
    val outline: Boolean = false,
    /** Own photo behind the keys ("" = none), darkened by [photoDim] (0–0.8) and blurred by [photoBlur] (0–2). */
    val photoPath: String = "",
    val photoDim: Float = 0.25f,
    val photoBlur: Int = 0,
) {
    enum class KeyShape(val label: String, val radiusDp: Int) {
        SQUARE("Square", 3),
        ROUNDED("Rounded", 8),
        SOFT("Soft", 14),
        PILL("Pill", 22),
    }

    /** The keyboard theme these choices make; pressed / special keys and contrast are worked out. */
    fun toTheme(): KeyboardTheme {
        val bg = Color(background)
        val keyColor = Color(key)
        val textColor = Color(text)
        val accentColor = Color(accent)
        val darkKeys = keyColor.compositeOver(bg).luminance() < 0.5f
        return KeyboardTheme(
            id = ID,
            name = "My theme",
            background = if (background2 == 0L) listOf(bg) else listOf(bg, Color(background2)),
            key = keyColor,
            keyPressed = lerp(keyColor, if (darkKeys) Color.White else Color.Black, 0.22f)
                .copy(alpha = (keyColor.alpha + 0.2f).coerceAtMost(1f)),
            specialKey = lerp(keyColor, if (darkKeys) Color.Black else Color.White, 0.18f),
            text = textColor,
            hint = accentColor,
            accent = accentColor,
            onAccent = if (accentColor.luminance() > 0.45f) Color(0xFF111111) else Color.White,
            toolbarText = textColor,
            keyBorder = if (outline) accentColor.copy(alpha = 0.6f) else null,
            keyRadius = shape.radiusDp.dp,
        )
    }

    fun encode(): String = JSONObject()
        .put("bg", background).put("bg2", background2).put("key", key)
        .put("text", text).put("accent", accent).put("shape", shape.name)
        .put("outline", outline).put("photo", photoPath)
        .put("dim", photoDim.toDouble()).put("blur", photoBlur)
        .toString()

    companion object {
        const val ID = "custom"

        fun decode(raw: String): CustomThemeSpec? = runCatching {
            if (raw.isBlank()) return null
            val o = JSONObject(raw)
            CustomThemeSpec(
                background = o.getLong("bg"),
                background2 = o.optLong("bg2", 0),
                key = o.getLong("key"),
                text = o.getLong("text"),
                accent = o.getLong("accent"),
                shape = runCatching { KeyShape.valueOf(o.optString("shape")) }.getOrDefault(KeyShape.ROUNDED),
                outline = o.optBoolean("outline", false),
                photoPath = o.optString("photo", ""),
                photoDim = o.optDouble("dim", 0.25).toFloat().coerceIn(0f, 0.8f),
                photoBlur = o.optInt("blur", 0).coerceIn(0, 2),
            )
        }.getOrNull()

        /** A starting point from a built-in theme, so "DIY" begins from what they use now. */
        fun from(theme: KeyboardTheme): CustomThemeSpec = CustomThemeSpec(
            background = theme.background.first().toArgb().toLong() and 0xFFFFFFFFL,
            background2 = if (theme.background.size > 1) theme.background[1].toArgb().toLong() and 0xFFFFFFFFL else 0L,
            key = theme.key.toArgb().toLong() and 0xFFFFFFFFL,
            text = theme.text.toArgb().toLong() and 0xFFFFFFFFL,
            accent = theme.accent.toArgb().toLong() and 0xFFFFFFFFL,
            shape = KeyShape.entries.minBy { kotlin.math.abs(it.radiusDp - theme.keyRadius.value) },
            outline = theme.keyBorder != null,
        )

        // ---- swatches offered in the studio

        /** Backgrounds: (top, bottom) — bottom 0 = solid. */
        val BACKGROUNDS: List<Pair<Long, Long>> = listOf(
            0xFF0B1F4B to 0xFF07142F, // navy
            0xFF0D0D0D to 0L, // black
            0xFF242727 to 0L, // charcoal
            0xFFE9ECF1 to 0L, // paper
            0xFF14361F to 0L, // tea green
            0xFF4A0E1F to 0xFF2A0611, // maroon
            0xFF2E1065 to 0xFF140630, // deep purple
            0xFF0F766E to 0xFF1E3A8A, // ocean
            0xFFFF6A3D to 0xFF7B2FF7, // sunset
            0xFF0F2027 to 0xFF2C5364, // aurora
            0xFFEE9CA7 to 0xFFFFDDE1, // rose
            0xFFF7B733 to 0xFFFC4A1A, // mango
            0xFF11998E to 0xFF38EF7D, // lime
            0xFF232526 to 0xFF414345, // graphite
        )

        /** Keys: solid colours and "glass" (translucent) for photo backgrounds. */
        val KEYS: List<Long> = listOf(
            0xFF16305F, 0xFF2A2D33, 0xFF242727, 0xFFFFFFFF, 0xFF1E4A2C, 0xFF5B1A2E, 0xFF3B1E7A,
            0x33FFFFFF, 0x55FFFFFF, 0x99FFFFFF, 0x33000000, 0x66000000, 0xCC000000, 0x00000000,
        )

        val TEXTS: List<Long> = listOf(
            0xFFFFFFFF, 0xFF111111, 0xFFF4B91F, 0xFFFFEB32, 0xFF4FC3F7, 0xFFFF8FB1, 0xFFA7F3D0,
        )

        val ACCENTS: List<Long> = listOf(
            0xFFF4B91F, 0xFFFFEB32, 0xFF4FC3F7, 0xFFFF4081, 0xFF22C55E, 0xFFFF7A00, 0xFFA855F7, 0xFFFFFFFF,
        )
    }
}

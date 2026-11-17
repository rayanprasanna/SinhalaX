package dev.valhalla.sinhalax.keyboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Shift key icons drawn as vectors, so they look the same on every phone
 * (the ⇧ / ⇪ text characters are missing from some fonts and show as a box).
 * Off = outline arrow, Once = filled arrow, Caps Lock = filled arrow with a bar.
 */
object ShiftIcons {

    private fun arrow(filled: Boolean, bar: Boolean): ImageVector =
        ImageVector.Builder(
            name = "Shift_${filled}_$bar",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Arrow: point at top, shoulders, stem.
            path(
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 3.5f)
                lineTo(20.5f, 12.5f)
                lineTo(15.5f, 12.5f)
                lineTo(15.5f, if (bar) 16.5f else 19.5f)
                lineTo(8.5f, if (bar) 16.5f else 19.5f)
                lineTo(8.5f, 12.5f)
                lineTo(3.5f, 12.5f)
                close()
            }
            if (bar) {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(8.5f, 18.5f)
                    lineTo(15.5f, 18.5f)
                    lineTo(15.5f, 20.5f)
                    lineTo(8.5f, 20.5f)
                    close()
                }
            }
        }.build()

    val Off: ImageVector by lazy { arrow(filled = false, bar = false) }
    val Once: ImageVector by lazy { arrow(filled = true, bar = false) }
    val Locked: ImageVector by lazy { arrow(filled = true, bar = true) }
}
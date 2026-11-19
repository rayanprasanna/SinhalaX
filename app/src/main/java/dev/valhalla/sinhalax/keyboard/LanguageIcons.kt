package dev.valhalla.sinhalax.keyboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Language icons for the keyboard: a globe with "a" (English) or "අ" (Sinhala) in the middle.
 * Clean vector redraw of the SinhalaX designs (24x24 grid, tint applied by Icon).
 * Letter outlines come from DejaVu Sans Bold ("a") and Noto Sans Sinhala Bold ("අ").
 */
object LanguageIcons {

    private const val OUTER = "M12 1.6999999999999993A10.3 10.3 0 1 1 12 22.3A10.3 10.3 0 1 1 12 1.6999999999999993Z"
    private const val LENS = "M12 1.6999999999999993A4.7 10.3 0 1 1 12 22.3A4.7 10.3 0 1 1 12 1.6999999999999993Z"
    private const val LINES = "M2.94 7.10H21.06M2.94 16.90H21.06M1.70 12H7.30M16.70 12H22.30"
    private const val LETTER_EN = "M12.09 12.46Q11.56 12.46 11.29 12.64Q11.02 12.82 11.02 13.18Q11.02 13.5 11.24 13.68Q11.45 13.87 11.84 13.87Q12.32 13.87 12.65 13.52Q12.98 13.18 12.98 12.66V12.46ZM14.7 11.82V14.86H12.98V14.07Q12.64 14.56 12.21 14.78Q11.78 15 11.16 15Q10.34 15 9.82 14.52Q9.3 14.03 9.3 13.26Q9.3 12.32 9.95 11.89Q10.59 11.45 11.97 11.45H12.98V11.31Q12.98 10.91 12.66 10.72Q12.34 10.53 11.66 10.53Q11.12 10.53 10.65 10.64Q10.17 10.75 9.77 10.97V9.67Q10.32 9.54 10.87 9.47Q11.42 9.4 11.97 9.4Q13.42 9.4 14.06 9.97Q14.7 10.54 14.7 11.82Z"
    private const val LETTER_SI = "M12.44 15.6V14.25Q12.16 14.28 11.89 14.28Q10.49 14.28 9.78 13.75Q9.07 13.22 9.07 12.32Q9.07 11.75 9.28 11.38Q9.49 11.02 9.86 10.81Q10.23 10.61 10.7 10.53Q11.18 10.45 11.7 10.45H12.4Q12.35 10.28 12.2 10.14Q12.05 10 11.71 10Q11.51 10 11.37 10.07Q11.23 10.14 11.23 10.28V10.3H9.86Q9.85 10.22 9.84 10.18Q9.84 10.15 9.84 10.1Q9.84 9.8 10.05 9.55Q10.25 9.29 10.62 9.15Q11 9 11.5 9Q12.07 9 12.46 9.18Q12.84 9.36 13.06 9.69Q13.19 9.54 13.33 9.4Q13.47 9.26 13.62 9.11H14.58Q14.49 9.32 14.47 9.51Q14.45 9.7 14.45 9.73Q14.45 10 14.53 10.17Q14.61 10.35 14.71 10.52Q14.8 10.67 14.87 10.85Q14.93 11.02 14.93 11.28Q14.93 11.69 14.62 11.91Q14.31 12.12 13.88 12.12Q13.71 12.12 13.53 12.1V12.92Q13.79 12.86 14.08 12.75Q14.37 12.65 14.64 12.53L14.67 13.62Q14.48 13.71 14.19 13.83Q13.9 13.94 13.53 14.04V15.6ZM13.53 10.73V11.3Q13.59 11.32 13.67 11.32Q13.9 11.32 13.97 11.19Q14.05 11.06 14.05 10.96Q14.05 10.85 14.01 10.77Q13.98 10.69 13.93 10.61Q13.87 10.51 13.82 10.4Q13.77 10.29 13.77 10.12Q13.69 10.23 13.61 10.38Q13.53 10.53 13.53 10.73ZM11.84 13.16Q12.02 13.16 12.17 13.15Q12.31 13.14 12.44 13.13V11.42H11.74Q11.43 11.42 11.14 11.47Q10.85 11.52 10.67 11.7Q10.49 11.88 10.49 12.28Q10.49 12.72 10.84 12.94Q11.19 13.16 11.84 13.16Z"

    val English: ImageVector by lazy { globe("LanguageEnglish", LETTER_EN) }
    val Sinhala: ImageVector by lazy { globe("LanguageSinhala", LETTER_SI) }

    /** Icon for the language you are typing in now. */
    fun forLanguage(language: Language): ImageVector =
        if (language == Language.SINHALA) Sinhala else English

    private fun globe(name: String, letter: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (d in listOf(OUTER, LENS, LINES)) {
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.3f,
                    strokeLineCap = StrokeCap.Round,
                )
            }
            addPath(pathData = addPathNodes(letter), fill = SolidColor(Color.Black))
        }.build()
}

package dev.valhalla.sinhalax.store

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Piano
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Picks an icon for a sound pack from its title, so the Audio Hub needs no icon field.
 * To force one, put "icon: <keyword>" in the audio's description in the admin panel,
 * e.g. "icon: drum". Unknown names fall back to a sound-wave icon.
 */
object SoundIcons {

    private val rules: List<Pair<List<String>, ImageVector>> = listOf(
        listOf("typewriter", "mechanical", "keyboard", "key", "clack", "thock") to Icons.Outlined.Keyboard,
        listOf("click", "tap", "tick", "touch") to Icons.Outlined.TouchApp,
        listOf("water", "drop", "drip", "rain") to Icons.Outlined.WaterDrop,
        listOf("bubble", "pop") to Icons.Outlined.BubbleChart,
        listOf("piano", "note", "melody") to Icons.Outlined.Piano,
        listOf("music", "guitar", "drum", "beat") to Icons.Outlined.MusicNote,
        listOf("bell", "chime", "ding") to Icons.Outlined.Notifications,
        listOf("game", "arcade", "8bit", "8-bit", "retro") to Icons.Outlined.SportsEsports,
        listOf("laser", "zap", "electric", "spark") to Icons.Outlined.Bolt,
        listOf("coin", "cash", "money") to Icons.Outlined.Toll,
        listOf("fire", "flame", "burn") to Icons.Outlined.Whatshot,
        listOf("party", "pop", "confetti", "celebrat") to Icons.Outlined.Celebration,
        listOf("cat", "dog", "duck", "animal", "meow", "bark") to Icons.Outlined.Pets,
        listOf("love", "heart", "kiss") to Icons.Outlined.Favorite,
        listOf("wave", "ocean", "sea", "wind") to Icons.Outlined.Waves,
    )

    fun forSound(title: String, description: String?): ImageVector {
        val forced = description
            ?.lineSequence()
            ?.firstNotNullOfOrNull { Regex("""(?i)^\s*icon\s*:\s*(\S+)""").find(it)?.groupValues?.get(1) }
        return match(forced) ?: match(title) ?: Icons.Outlined.GraphicEq
    }

    private fun match(text: String?): ImageVector? {
        if (text.isNullOrBlank()) return null
        val t = text.lowercase()
        return rules.firstOrNull { (words, _) -> words.any { it in t } }?.second
    }
}
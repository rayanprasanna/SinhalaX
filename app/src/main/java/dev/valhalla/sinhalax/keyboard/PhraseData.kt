package dev.valhalla.sinhalax.keyboard

/**
 * Phrase studio content: ready Sinhala phrases by topic, and decorations to wrap them in.
 * Joined letters use an explicit zero-width joiner (‍): ්‍ය (yansaya), ්‍ර (rakaransaya),
 * ර්‍ (repaya), so they render the same on every phone.
 */
object PhraseData {

    class Category(val label: String, val phrases: List<String>)

    val categories: List<Category> = listOf(
        Category(
            "Greetings",
            listOf(
                "සුභ උදෑසනක්! ☀️",
                "සුභ දවසක්! 🌼",
                "සුභ සන්ධ්‍යාවක්! 🌇",
                "සුභ රාත්‍රියක්! 🌙✨",
                "ආයුබෝවන්! 🙏",
                "කොහොමද? 😊",
            ),
        ),
        Category(
            "Wishes",
            listOf(
                "සුභ උපන්දිනයක් වේවා! 🎂🎉",
                "ජය වේවා! 🏆",
                "සාර්‍ථක වේවා! 🌟",
                "සුභ පැතුම්! 💐",
                "ඉක්මනින් සුව වේවා! 🌿",
                "සුභ ගමන්! ✈️",
                "සුභ විවාහ දිවියක් වේවා! 💍",
            ),
        ),
        Category(
            "Love",
            listOf(
                "ආදරෙයි! ❤️",
                "මම ඔයාට ගොඩක් ආදරෙයි 💕",
                "ඔයාව මතක් වෙනවා 🥺",
                "හීනෙන් හමුවෙමු 🌙💞",
            ),
        ),
        Category(
            "Thanks",
            listOf(
                "බොහොම ස්තූතියි! 🙏",
                "ඔබට ස්තූතියි! 😊",
                "කමක් නෑ 😊",
                "සමාවෙන්න 🙏",
            ),
        ),
        Category(
            "Daily",
            listOf(
                "හරි 👍",
                "එන්නම් 🏃",
                "පොඩ්ඩක් ඉන්න ⏳",
                "පස්සේ කතා කරමු 📞",
                "කෑවද? 🍛",
                "ගෙදර ගියාද? 🏠",
                "ලස්සනයි! 😍",
            ),
        ),
        Category(
            "Festivals",
            listOf(
                "සුභ අලුත් අවුරුද්දක් වේවා! 🌞🌺",
                "සුභ වෙසක් මංගල්‍යයක් වේවා! 🏮",
                "සුභ පොසොන් මංගල්‍යයක් වේවා! 🪷",
                "සුභ නත්තලක් වේවා! 🎄",
                "සුභ නව වසරක් වේවා! 🎆",
                "සුභ දීපවාලියක් වේවා! 🪔",
                "සුභ තෛපොංගල් දිනයක් වේවා! 🌾",
                "ඊද් මුබාරක්! 🌙",
            ),
        ),
        Category(
            "Sympathy",
            listOf(
                "ගැඹුරු ශෝකය පළ කරමි 🙏",
                "නිවන් සුව ලැබේවා! 🙏",
                "ඔබ සමඟ අපි ඉන්නවා 🤍",
            ),
        ),
    )

    /** Frames to wrap a phrase in; "%s" is the phrase. */
    enum class Decoration(val label: String, val pattern: String) {
        NONE("Plain", "%s"),
        SPARKLE("✨", "✨ %s ✨"),
        HEARTS("💖", "💖 %s 💖"),
        FLOWERS("🌸", "🌸 %s 🌸"),
        RIBBON("🎀", "🎀 %s 🎀"),
        BRACKETS("『』", "『 %s 』"),
        ROYAL("꧁꧂", "꧁ %s ꧂"),
        STARS("★彡", "★彡 %s 彡★"),
        LINE("━━", "━━ %s ━━"),
        ;

        fun apply(phrase: String): String = pattern.replace("%s", phrase)
    }
}

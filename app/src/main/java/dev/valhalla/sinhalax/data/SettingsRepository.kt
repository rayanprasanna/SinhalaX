package dev.valhalla.sinhalax.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "keyboard_settings")

/** Shared by the companion app and the IME service (same process, one DataStore instance). */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.settingsStore

    val settings: Flow<KeyboardSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map(::read)

    suspend fun update(transform: (KeyboardSettings) -> KeyboardSettings) {
        store.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private fun read(p: Preferences): KeyboardSettings {
        val d = KeyboardSettings()
        return KeyboardSettings(
            themeId = p[Keys.THEME] ?: d.themeId,
            startInSinhala = p[Keys.START_SINHALA] ?: d.startInSinhala,
            livePreview = p[Keys.LIVE_PREVIEW] ?: d.livePreview,
            autoCapitalize = p[Keys.AUTO_CAPS] ?: d.autoCapitalize,
            wordSuggestions = p[Keys.SUGGESTIONS] ?: d.wordSuggestions,
            autoCorrect = p[Keys.AUTO_CORRECT] ?: d.autoCorrect,
            learnWords = p[Keys.LEARN] ?: d.learnWords,
            showHints = p[Keys.HINTS] ?: d.showHints,
            numberRow = p[Keys.NUMBER_ROW] ?: d.numberRow,
            heightScale = p[Keys.HEIGHT] ?: d.heightScale,
            keyFontScale = p[Keys.FONT] ?: d.keyFontScale,
            bottomPaddingDp = p[Keys.BOTTOM_PADDING] ?: d.bottomPaddingDp,
            vibrate = p[Keys.VIBRATE] ?: d.vibrate,
            sound = p[Keys.SOUND] ?: d.sound,
            clipboardHistory = p[Keys.CLIPBOARD] ?: d.clipboardHistory,
            backgroundId = p[Keys.BG_ID] ?: d.backgroundId,
            backgroundPath = p[Keys.BG_PATH] ?: d.backgroundPath,
            backgroundKeyTextColor = p[Keys.BG_TEXT] ?: d.backgroundKeyTextColor,
            backgroundDominantColor = p[Keys.BG_DOMINANT] ?: d.backgroundDominantColor,
            soundPackId = p[Keys.SOUND_ID] ?: d.soundPackId,
            soundPackPath = p[Keys.SOUND_PATH] ?: d.soundPackPath,
            soundVolume = p[Keys.SOUND_VOLUME] ?: d.soundVolume,
            incognito = p[Keys.INCOGNITO] ?: d.incognito,
            oneHand = p[Keys.ONE_HAND] ?: d.oneHand,
            toolbarItems = p[Keys.TOOLBAR] ?: d.toolbarItems,
            shortcutsEnabled = p[Keys.SHORTCUTS] ?: d.shortcutsEnabled,
            slideGestures = p[Keys.SLIDE_GESTURES] ?: d.slideGestures,
            emojiStyle = p[Keys.EMOJI_STYLE] ?: d.emojiStyle,
            floating = p[Keys.FLOATING] ?: d.floating,
            floatX = p[Keys.FLOAT_X] ?: d.floatX,
            floatY = p[Keys.FLOAT_Y] ?: d.floatY,
            floatWidth = p[Keys.FLOAT_WIDTH] ?: d.floatWidth,
            customTheme = p[Keys.CUSTOM_THEME] ?: d.customTheme,
        )
    }

    private fun write(p: MutablePreferences, s: KeyboardSettings) {
        p[Keys.THEME] = s.themeId
        p[Keys.START_SINHALA] = s.startInSinhala
        p[Keys.LIVE_PREVIEW] = s.livePreview
        p[Keys.AUTO_CAPS] = s.autoCapitalize
        p[Keys.SUGGESTIONS] = s.wordSuggestions
        p[Keys.AUTO_CORRECT] = s.autoCorrect
        p[Keys.LEARN] = s.learnWords
        p[Keys.HINTS] = s.showHints
        p[Keys.NUMBER_ROW] = s.numberRow
        p[Keys.HEIGHT] = s.heightScale
        p[Keys.FONT] = s.keyFontScale
        p[Keys.BOTTOM_PADDING] = s.bottomPaddingDp
        p[Keys.VIBRATE] = s.vibrate
        p[Keys.SOUND] = s.sound
        p[Keys.CLIPBOARD] = s.clipboardHistory
        p[Keys.BG_ID] = s.backgroundId
        p[Keys.BG_PATH] = s.backgroundPath
        p[Keys.BG_TEXT] = s.backgroundKeyTextColor
        p[Keys.BG_DOMINANT] = s.backgroundDominantColor
        p[Keys.SOUND_ID] = s.soundPackId
        p[Keys.SOUND_PATH] = s.soundPackPath
        p[Keys.SOUND_VOLUME] = s.soundVolume
        p[Keys.INCOGNITO] = s.incognito
        p[Keys.ONE_HAND] = s.oneHand
        p[Keys.TOOLBAR] = s.toolbarItems
        p[Keys.SHORTCUTS] = s.shortcutsEnabled
        p[Keys.SLIDE_GESTURES] = s.slideGestures
        p[Keys.EMOJI_STYLE] = s.emojiStyle
        p[Keys.FLOATING] = s.floating
        p[Keys.FLOAT_X] = s.floatX
        p[Keys.FLOAT_Y] = s.floatY
        p[Keys.FLOAT_WIDTH] = s.floatWidth
        p[Keys.CUSTOM_THEME] = s.customTheme
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val START_SINHALA = booleanPreferencesKey("start_sinhala")
        val LIVE_PREVIEW = booleanPreferencesKey("live_preview")
        val AUTO_CAPS = booleanPreferencesKey("auto_capitalize")
        val SUGGESTIONS = booleanPreferencesKey("word_suggestions")
        val AUTO_CORRECT = booleanPreferencesKey("auto_correct")
        val LEARN = booleanPreferencesKey("learn_words")
        val HINTS = booleanPreferencesKey("hints")
        val NUMBER_ROW = booleanPreferencesKey("number_row")
        val HEIGHT = floatPreferencesKey("height_scale")
        val FONT = floatPreferencesKey("font_scale")
        val BOTTOM_PADDING = intPreferencesKey("bottom_padding")
        val VIBRATE = booleanPreferencesKey("vibrate")
        val SOUND = booleanPreferencesKey("sound")
        val CLIPBOARD = booleanPreferencesKey("clipboard_history")
        val BG_ID = stringPreferencesKey("bg_id")
        val BG_PATH = stringPreferencesKey("bg_path")
        val BG_TEXT = stringPreferencesKey("bg_key_text")
        val BG_DOMINANT = stringPreferencesKey("bg_dominant")
        val SOUND_ID = stringPreferencesKey("sound_pack_id")
        val SOUND_PATH = stringPreferencesKey("sound_pack_path")
        val SOUND_VOLUME = floatPreferencesKey("sound_volume")
        val INCOGNITO = booleanPreferencesKey("incognito")
        val ONE_HAND = stringPreferencesKey("one_hand")
        val TOOLBAR = stringPreferencesKey("toolbar_items")
        val SHORTCUTS = booleanPreferencesKey("shortcuts_enabled")
        val SLIDE_GESTURES = booleanPreferencesKey("slide_gestures")
        val EMOJI_STYLE = stringPreferencesKey("emoji_style")
        val FLOATING = booleanPreferencesKey("floating")
        val FLOAT_X = floatPreferencesKey("float_x")
        val FLOAT_Y = floatPreferencesKey("float_y")
        val FLOAT_WIDTH = floatPreferencesKey("float_width")
        val CUSTOM_THEME = stringPreferencesKey("custom_theme")
    }
}

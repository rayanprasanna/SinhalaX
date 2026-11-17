package dev.valhalla.sinhalax.keyboard

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.valhalla.sinhalax.data.KeyboardSettings
import dev.valhalla.sinhalax.engine.SinglishScheme
import dev.valhalla.sinhalax.engine.SinglishTransliterator
import dev.valhalla.sinhalax.prediction.Predictor

enum class Language { SINHALA, ENGLISH }
enum class KeyMode { LETTERS, SYMBOLS, SYMBOLS2 }
enum class ShiftState { OFF, ONCE, LOCKED }
enum class Panel { KEYS, EMOJI, CLIPBOARD }

enum class EnterAction(val imeAction: Int) {
    NEWLINE(EditorInfo.IME_ACTION_NONE),
    GO(EditorInfo.IME_ACTION_GO),
    SEARCH(EditorInfo.IME_ACTION_SEARCH),
    SEND(EditorInfo.IME_ACTION_SEND),
    NEXT(EditorInfo.IME_ACTION_NEXT),
    DONE(EditorInfo.IME_ACTION_DONE),
    PREVIOUS(EditorInfo.IME_ACTION_PREVIOUS);

    companion object {
        fun from(info: EditorInfo?): EnterAction {
            if (info == null) return NEWLINE
            if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return NEWLINE
            return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_GO -> GO
                EditorInfo.IME_ACTION_SEARCH -> SEARCH
                EditorInfo.IME_ACTION_SEND -> SEND
                EditorInfo.IME_ACTION_NEXT -> NEXT
                EditorInfo.IME_ACTION_DONE -> DONE
                EditorInfo.IME_ACTION_PREVIOUS -> PREVIOUS
                else -> NEWLINE
            }
        }
    }
}

/**
 * All keyboard behaviour lives here, independent of the UI.
 *
 * In Sinhala mode, Latin letters go into a composing buffer. The Sinhala result is shown
 * in the editor as composing text and committed on space, punctuation, enter or a suggestion tap.
 *
 * [inputConnection] returns null in the in-app preview, so every call is null-safe.
 * [predictor] returns null while the dictionary is still loading; plain conversion is used meanwhile.
 * [onWordLearned] receives committed Sinhala words for the on-device personal dictionary.
 */
class KeyboardController(
    private val engine: SinglishTransliterator,
    private val inputConnection: () -> InputConnection?,
    private val sendKey: (Int) -> Unit,
    private val predictor: () -> Predictor? = { null },
    private val onWordLearned: (String) -> Unit = {},
) {
    var language by mutableStateOf(Language.SINHALA); private set
    var mode by mutableStateOf(KeyMode.LETTERS); private set
    var shift by mutableStateOf(ShiftState.OFF); private set
    var panel by mutableStateOf(Panel.KEYS); private set
    var candidates by mutableStateOf<List<String>>(emptyList()); private set
    var enterAction by mutableStateOf(EnterAction.NEWLINE); private set

    /** Password and similar fields: English only, no preview, nothing captured. */
    var restrictedField by mutableStateOf(false); private set

    private val composing = StringBuilder()
    private var editorInfo: EditorInfo? = null
    private var preferredLanguage = Language.SINHALA
    private var livePreview = true
    private var autoCapitalize = true
    private var wordSuggestions = true
    private var autoCorrect = true
    private var learnWords = true
    private var settingsApplied = false

    /** What space will commit for the current word (may be an auto-correction). */
    private var bestGuess = ""

    /** Set right after space applied an auto-correction: (corrected, what they typed). Backspace undoes it. */
    private var lastAutoCorrection: Pair<String, String>? = null

    /** Every character that begins a key in the phonetic scheme (decides which capitals mean something). */
    private val schemeStartChars: Set<Char> = (
        SinglishScheme.consonants.keys + SinglishScheme.vowelSigns.keys +
            SinglishScheme.independentVowels.keys + SinglishScheme.specials.keys
        ).map { it.first() }.toSet()

    // ---------------------------------------------------------------- lifecycle

    fun applySettings(settings: KeyboardSettings) {
        livePreview = settings.livePreview
        val capsChanged = autoCapitalize != settings.autoCapitalize
        autoCapitalize = settings.autoCapitalize
        wordSuggestions = settings.wordSuggestions
        autoCorrect = settings.autoCorrect
        learnWords = settings.learnWords
        if (!settingsApplied) {
            settingsApplied = true
            preferredLanguage = if (settings.startInSinhala) Language.SINHALA else Language.ENGLISH
            if (!restrictedField) language = preferredLanguage
        }
        if (composing.isNotEmpty()) updateComposing()
        if (capsChanged) updateAutoCaps()
    }

    fun onStartInput(info: EditorInfo?) {
        composing.clear()
        candidates = emptyList()
        bestGuess = ""
        lastAutoCorrection = null
        editorInfo = info
        panel = Panel.KEYS

        val inputType = info?.inputType ?: 0
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION

        restrictedField = isPassword(inputClass, variation)
        val englishOnly = restrictedField || (
            inputClass == InputType.TYPE_CLASS_TEXT && variation in setOf(
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_URI,
            )
            )
        language = if (englishOnly) Language.ENGLISH else preferredLanguage
        mode = when (inputClass) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> KeyMode.SYMBOLS
            else -> KeyMode.LETTERS
        }
        enterAction = EnterAction.from(info)
        shift = ShiftState.OFF
        updateAutoCaps()
    }

    fun onStartInputView() {
        panel = Panel.KEYS
    }

    fun reset() {
        composing.clear()
        candidates = emptyList()
        bestGuess = ""
        lastAutoCorrection = null
    }

    /** Called from InputMethodService.onUpdateSelection. Drops the composing word if the cursor moved away. */
    fun onSelectionChanged(newSelStart: Int, newSelEnd: Int, candidatesEnd: Int) {
        if (composing.isNotEmpty() && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            composing.clear()
            candidates = emptyList()
            bestGuess = ""
            inputConnection()?.finishComposingText()
        }
    }

    // ---------------------------------------------------------------- input

    fun onAction(action: KeyAction) {
        val undo = lastAutoCorrection
        lastAutoCorrection = null
        when (action) {
            is KeyAction.Text -> typeText(action.value)
            KeyAction.Shift -> shift = when (shift) {
                ShiftState.OFF -> ShiftState.ONCE
                ShiftState.ONCE -> ShiftState.LOCKED
                ShiftState.LOCKED -> ShiftState.OFF
            }
            KeyAction.Delete -> if (undo == null || !undoAutoCorrection(undo)) delete()
            KeyAction.Space -> {
                commitWord(applyCorrection = true)
                commit(" ")
                updateAutoCaps()
            }
            KeyAction.Enter -> enter()
            KeyAction.ToLetters -> mode = KeyMode.LETTERS
            KeyAction.ToSymbols -> mode = KeyMode.SYMBOLS
            KeyAction.ToSymbols2 -> mode = KeyMode.SYMBOLS2
            KeyAction.ToggleLanguage -> toggleLanguage()
            KeyAction.None -> Unit
        }
    }

    fun pickCandidate(text: String) {
        val ic = inputConnection()
        lastAutoCorrection = null
        composing.clear()
        candidates = emptyList()
        bestGuess = ""
        ic?.commitText(text, 1)
        ic?.commitText(" ", 1)
        learn(text) // an explicit choice — also stops future auto-correction of this spelling
    }

    /** Emoji, kaomoji, clipboard items. */
    fun insertText(text: String) {
        lastAutoCorrection = null
        commitComposing()
        commit(text)
    }

    fun moveCursor(direction: Int) {
        lastAutoCorrection = null
        commitComposing()
        sendKey(if (direction > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
    }

    fun toggleLanguage() {
        if (restrictedField) return
        commitComposing()
        language = if (language == Language.SINHALA) Language.ENGLISH else Language.SINHALA
        preferredLanguage = language
        shift = ShiftState.OFF
        mode = KeyMode.LETTERS
        updateAutoCaps()
    }

    fun togglePanel(target: Panel) {
        commitComposing()
        panel = if (panel == target) Panel.KEYS else target
    }

    fun showKeys() {
        panel = Panel.KEYS
    }

    /** Keeps the word exactly as typed (no auto-correction). */
    fun commitComposing() {
        commitWord(applyCorrection = false)
    }

    // ---------------------------------------------------------------- labels

    /** Label shown on a letter key: every letter shows as a capital while Shift is on. */
    fun displayLabel(base: String): String {
        if (base.length != 1 || !base[0].isLetter() || shift == ShiftState.OFF) return base
        return base.uppercase()
    }

    /**
     * Small Sinhala letter shown on a key: what the key will actually type.
     * Capitals with no phonetic meaning (E, F, I…) type the lowercase letter, so they show its hint.
     */
    fun sinhalaHint(base: String): String? {
        if (language != Language.SINHALA || mode != KeyMode.LETTERS || base.length != 1) return null
        val key = phoneticKey(base)
        return SinglishScheme.consonants[key]
            ?: SinglishScheme.independentVowels[key]
            ?: SinglishScheme.specials[key]
    }

    private fun phoneticKey(base: String): String {
        if (shift == ShiftState.OFF || !base[0].isLetter()) return base
        val upper = base.uppercase()
        return if (upper[0] in schemeStartChars) upper else base
    }

    // ---------------------------------------------------------------- internals

    private fun typeText(raw: String) {
        val text = if (shift != ShiftState.OFF && raw.length == 1 && raw[0].isLetter()) raw.uppercase() else raw
        val c = text.singleOrNull()
        val phonetic = c != null && (c in 'a'..'z' || c in 'A'..'Z') &&
            language == Language.SINHALA && mode == KeyMode.LETTERS && !restrictedField

        if (phonetic) {
            val letter = if (c.isUpperCase() && c !in schemeStartChars) c.lowercaseChar() else c
            composing.append(letter)
            updateComposing()
        } else {
            // Punctuation ends a word like space does, so it applies the auto-correction too.
            commitWord(applyCorrection = c != null && !c.isLetterOrDigit())
            commit(text)
        }
        if (shift == ShiftState.ONCE) shift = ShiftState.OFF
        updateAutoCaps()
    }

    private fun updateComposing() {
        val ic = inputConnection()
        val latin = composing.toString()
        if (latin.isEmpty()) {
            ic?.setComposingText("", 1)
            ic?.finishComposingText()
            candidates = emptyList()
            return
        }
        val primary = engine.transliterate(latin)
        ic?.setComposingText(primary, 1)

        val predictor = if (wordSuggestions) predictor() else null
        val list: List<String>
        if (predictor != null) {
            val prediction = predictor.predict(latin, limit = 3, autoCorrect = autoCorrect)
            bestGuess = prediction.best
            list = prediction.suggestions
        } else {
            bestGuess = primary
            list = engine.candidates(latin, 3)
        }
        // First chip = what space commits; last chip keeps the English letters.
        candidates = if (livePreview) (list + latin).distinct() else emptyList()
    }

    /**
     * Ends the current Sinhala word. With [applyCorrection], the auto-corrected word replaces
     * what was typed (space, punctuation, enter). Returns true if a correction was applied.
     */
    private fun commitWord(applyCorrection: Boolean): Boolean {
        if (composing.isEmpty()) return false
        val primary = engine.transliterate(composing.toString())
        val corrected = applyCorrection && bestGuess.isNotEmpty() && bestGuess != primary
        val word = if (corrected) bestGuess else primary
        val ic = inputConnection()
        if (corrected) ic?.commitText(word, 1) else ic?.finishComposingText()
        composing.clear()
        candidates = emptyList()
        bestGuess = ""
        learn(word)
        if (corrected) lastAutoCorrection = word to primary
        return corrected
    }

    /** Backspace straight after an auto-correction puts back what they typed (and remembers that choice). */
    private fun undoAutoCorrection(undo: Pair<String, String>): Boolean {
        val (corrected, original) = undo
        val ic = inputConnection() ?: return false
        val before = ic.getTextBeforeCursor(corrected.length + 1, 0)?.toString()
        if (before != "$corrected ") return false
        ic.deleteSurroundingText(corrected.length + 1, 0)
        ic.commitText(original, 1)
        learn(original)
        updateAutoCaps()
        return true
    }

    private fun learn(word: String) {
        if (!learnWords || restrictedField || word.none { it in '\u0D80'..'\u0DFF' }) return
        val info = editorInfo
        if (info != null && info.imeOptions and IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return // incognito tabs etc.
        onWordLearned(word)
    }

    private fun delete() {
        if (composing.isNotEmpty()) {
            composing.deleteCharAt(composing.length - 1)
            updateComposing()
            return
        }
        val ic = inputConnection() ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        // Don't leave a dangling zero-width joiner behind (ක්‍ර → ක්‍ → ක්).
        val before = ic.getTextBeforeCursor(2, 0)
        if (before != null && before.length == 2 && before[0] == '\u200D') {
            ic.deleteSurroundingText(2, 0)
        } else {
            sendKey(KeyEvent.KEYCODE_DEL)
        }
        updateAutoCaps()
    }

    private fun enter() {
        commitWord(applyCorrection = true)
        val ic = inputConnection() ?: return
        if (enterAction == EnterAction.NEWLINE) {
            sendKey(KeyEvent.KEYCODE_ENTER)
            updateAutoCaps()
        } else {
            ic.performEditorAction(enterAction.imeAction)
        }
    }

    private fun commit(text: String) {
        inputConnection()?.commitText(text, 1)
    }

    /**
     * English only: turns Shift on for the first letter of a sentence.
     * Uses the field's own capitalisation flag, or (with Auto-capitalise on) our own sentence
     * detection for ordinary text fields that don't ask for it.
     */
    private fun updateAutoCaps() {
        if (restrictedField || language != Language.ENGLISH || mode != KeyMode.LETTERS || shift == ShiftState.LOCKED) return
        if (!autoCapitalize) {
            if (shift == ShiftState.ONCE) shift = ShiftState.OFF
            return
        }
        val info = editorInfo ?: return
        val ic = inputConnection() ?: return
        val caps = ic.getCursorCapsMode(info.inputType) != 0 ||
            (allowsSentenceCaps(info.inputType) && isSentenceStart(ic.getTextBeforeCursor(3, 0)))
        shift = if (caps) ShiftState.ONCE else ShiftState.OFF
    }

    /** Plain text, messages and subjects — not email, web address, password or number fields. */
    private fun allowsSentenceCaps(inputType: Int): Boolean {
        return inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT && when (inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_NORMAL,
            InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT,
            InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
            InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,
            -> true

            else -> false
        }
    }

    /** True at the very start, or after ". ", "? ", "! " or a new line. */
    private fun isSentenceStart(before: CharSequence?): Boolean {
        if (before == null) return false
        if (before.isEmpty()) return true
        val last = before.last()
        if (last == '\n') return true
        if (!last.isWhitespace()) return false
        val trimmed = before.trimEnd()
        return trimmed.isEmpty() || trimmed.last() in ".?!\n"
    }

    private fun isPassword(inputClass: Int, variation: Int): Boolean =
        (inputClass == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )) || (inputClass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)

    private companion object {
        /** EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING (API 26), as a literal so it works on every version. */
        const val IME_FLAG_NO_PERSONALIZED_LEARNING = 0x1000000
    }
}

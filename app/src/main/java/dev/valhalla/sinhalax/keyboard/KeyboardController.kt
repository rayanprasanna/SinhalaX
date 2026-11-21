package dev.valhalla.sinhalax.keyboard

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.valhalla.sinhalax.data.KeyboardSettings
import dev.valhalla.sinhalax.engine.FancyText
import dev.valhalla.sinhalax.engine.SinglishScheme
import dev.valhalla.sinhalax.engine.SinglishTransliterator
import dev.valhalla.sinhalax.prediction.Predictor

enum class Language { SINHALA, ENGLISH }
enum class KeyMode { LETTERS, SYMBOLS, SYMBOLS2 }
enum class ShiftState { OFF, ONCE, LOCKED }
enum class Panel {
    KEYS, EMOJI, CLIPBOARD, FANCY, VOICE,
    // Tools hub (v0.7) and the tools that open in place of the keys
    TOOLS, TEXT_EDIT, NUMPAD, CALCULATOR, REPEAT,
    // Phase 2 (v0.8)
    SECRET, PHRASES,
    // Phase 3 (v0.9)
    EMOJI_STYLE,
}

/** One tap of ✓ Translate: what to translate and from which language. */
data class TranslateRequest(val id: Int, val text: String, val from: Language)

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
 * [appConnection] returns null in the in-app preview, so every call is null-safe.
 *
 * Translate mode: while [translateActive], everything typed (Singlish conversion, suggestions,
 * voice) goes into the keyboard's own translate box ([translateConnection]) instead of the app.
 * [translateText] mirrors the box. ✓ Translate (or enter) publishes it as [translateRequest]; the
 * IME service translates it, types the result into the app and calls [onTranslated].
 * [predictor] returns null while the dictionary is still loading; plain conversion is used meanwhile.
 * [onWordLearned] receives committed Sinhala words for the on-device personal dictionary.
 */
class KeyboardController(
    private val engine: SinglishTransliterator,
    private val appConnection: () -> InputConnection?,
    private val sendAppKey: (Int) -> Unit,
    private val predictor: () -> Predictor? = { null },
    private val onWordLearned: (String) -> Unit = {},
    /** The translate box's editor (null in the preview). */
    private val translateConnection: () -> InputConnection? = { null },
    /** Shortcuts (text expansion): lower-case key → text. */
    private val shortcuts: () -> Map<String, String> = { emptyMap() },
) {
    var language by mutableStateOf(Language.SINHALA); private set
    var mode by mutableStateOf(KeyMode.LETTERS); private set
    var shift by mutableStateOf(ShiftState.OFF); private set
    var panel by mutableStateOf(Panel.KEYS); private set
    var candidates by mutableStateOf<List<String>>(emptyList()); private set
    var enterAction by mutableStateOf(EnterAction.NEWLINE); private set

    /** Password and similar fields: English only, no preview, nothing captured. */
    var restrictedField by mutableStateOf(false); private set

    /**
     * Media search (GIFs, stickers, memes): while [searchActive], keys type into [searchQuery]
     * inside the keyboard instead of into the app. Enter publishes the query as [submittedSearch].
     */
    var searchActive by mutableStateOf(false); private set
    var searchQuery by mutableStateOf(""); private set
    var submittedSearch by mutableStateOf<String?>(null); private set

    /** Translate mode: keys type into the translate box; [translateText] is what it holds. */
    var translateActive by mutableStateOf(false); private set
    var translateText by mutableStateOf(""); private set

    /** Waiting to be translated (set by ✓ Translate / enter), or null. */
    var translateRequest by mutableStateOf<TranslateRequest?>(null); private set
    private var nextRequestId = 0

    /** Active fancy-text style (English letters and digits are typed in it), or null. */
    var fancyStyle by mutableStateOf<FancyText.Style?>(null); private set

    private val composing = StringBuilder()

    /** True while voice typing shows words that aren't final yet (as composing text). */
    private var voiceComposing = false

    /**
     * Undo: what SinhalaX put in, newest last, in word-sized pieces ("මම ", "hello, ", a pasted
     * text, an emoji…). Undo removes the newest piece if it is still right before the cursor.
     */
    private val undoLog = ArrayDeque<String>()
    private val redoLog = ArrayDeque<String>()
    private var redoing = false
    private var voiceNeedsSpace = false
    private var editorInfo: EditorInfo? = null
    private var preferredLanguage = Language.SINHALA
    private var livePreview = true
    private var autoCapitalize = true
    private var wordSuggestions = true
    private var autoCorrect = true
    private var learnWords = true
    private var shortcutsEnabled = true

    /** Incognito: nothing is learned or remembered while on (also hides clipboard / recents saving). */
    var incognito by mutableStateOf(false); private set
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
        shortcutsEnabled = settings.shortcutsEnabled
        incognito = settings.incognito
        if (!settingsApplied) {
            settingsApplied = true
            preferredLanguage = if (settings.startInSinhala) Language.SINHALA else Language.ENGLISH
            if (!restrictedField) language = preferredLanguage
        }
        if (composing.isNotEmpty()) updateComposing()
        if (capsChanged) updateAutoCaps()
    }

    fun onStartInput(info: EditorInfo?) {
        if (translateActive) {
            translateActive = false
            translateRequest = null
            clearTranslateBox()
        }
        voiceComposing = false
        clearUndo()
        searchActive = false
        searchQuery = ""
        submittedSearch = null
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

    /** The keyboard is being shown again: start on the letters (see SinhalaXImeService.onStartInputView). */
    fun onStartInputView() {
        if (searchActive) cancelSearch()
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
        // While translating, the app's field only shows the translation; the word being typed is in the box.
        if (translateActive) return
        if (composing.isNotEmpty() && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            composing.clear()
            candidates = emptyList()
            bestGuess = ""
            inputConnection()?.finishComposingText()
        }
    }

    // ---------------------------------------------------------------- input

    fun onAction(action: KeyAction) {
        if (searchActive && onSearchAction(action)) return
        if (translateActive && action == KeyAction.Enter) {
            submitTranslate()
            return
        }
        endVoiceText()
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
                if (!expandShortcut()) {
                    commitWord(applyCorrection = true)
                    commit(" ")
                }
                updateAutoCaps()
            }
            KeyAction.Enter -> enter()
            KeyAction.ToLetters -> mode = KeyMode.LETTERS
            KeyAction.ToSymbols -> mode = KeyMode.SYMBOLS
            KeyAction.ToSymbols2 -> mode = KeyMode.SYMBOLS2
            KeyAction.ToggleLanguage -> toggleLanguage()
            KeyAction.None -> Unit
        }
        refreshTranslateText()
    }

    fun pickCandidate(text: String) {
        val ic = inputConnection()
        lastAutoCorrection = null
        composing.clear()
        candidates = emptyList()
        bestGuess = ""
        ic?.commitText(text, 1)
        ic?.commitText(" ", 1)
        recordInsert(text)
        recordInsert(" ")
        learn(text) // an explicit choice — also stops future auto-correction of this spelling
        refreshTranslateText()
    }

    /** Emoji, kaomoji, clipboard items. */
    fun insertText(text: String) {
        lastAutoCorrection = null
        endVoiceText()
        commitComposing()
        commit(text)
        refreshTranslateText()
    }

    fun moveCursor(direction: Int) {
        if (translateActive) return // the box always types at its end
        lastAutoCorrection = null
        commitComposing()
        sendKey(if (direction > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
    }

    fun toggleLanguage() {
        if (restrictedField) return
        endVoiceText()
        commitComposing()
        language = if (language == Language.SINHALA) Language.ENGLISH else Language.SINHALA
        preferredLanguage = language
        shift = ShiftState.OFF
        mode = KeyMode.LETTERS
        updateAutoCaps()
        refreshTranslateText()
    }

    fun togglePanel(target: Panel) {
        searchActive = false
        endVoiceText()
        commitComposing()
        panel = if (panel == target) Panel.KEYS else target
    }

    // ---------------------------------------------------------------- translate

    /**
     * Opens the translate box. Type in the keyboard's language (Singlish turns into Sinhala as usual);
     * the translation goes into the app. The language key flips the direction.
     */
    fun startTranslate() {
        if (restrictedField) return
        translateRequest = null
        endVoiceText()
        commitComposing()
        searchActive = false
        if (panel != Panel.VOICE) panel = Panel.KEYS
        clearTranslateBox()
        clearUndo()
        translateActive = true
        mode = KeyMode.LETTERS
        shift = ShiftState.OFF
        updateAutoCaps()
    }

    /** Closes the box (✕). Anything not translated yet is dropped. */
    fun stopTranslate() {
        if (!translateActive) return
        endVoiceText()
        commitComposing()
        translateActive = false
        translateRequest = null
        clearTranslateBox()
        clearUndo()
        updateAutoCaps()
    }

    /**
     * ✓ Translate / enter: asks for the box to be translated (one request per tap, like Z Sri).
     * The direction follows the keyboard language: Sinhala → English, English → Sinhala.
     */
    fun submitTranslate() {
        if (!translateActive || translateRequest != null) return
        endVoiceText()
        commitComposing()
        refreshTranslateText()
        val text = translateText.trim()
        if (text.isEmpty()) return
        translateRequest = TranslateRequest(++nextRequestId, text, language)
    }

    /**
     * Called by the service when [request] is done. On success the result is already in the app,
     * so the box is emptied for the next sentence; on failure the text stays to try again.
     */
    fun onTranslated(request: TranslateRequest, success: Boolean) {
        if (translateRequest?.id != request.id) return
        translateRequest = null
        // Only empty the box if nothing new was typed while waiting.
        if (success && translateActive && translateText.trim() == request.text && composing.isEmpty()) {
            composing.clear()
            candidates = emptyList()
            bestGuess = ""
            clearTranslateBox()
            updateAutoCaps()
        }
    }

    /** Puts [text] (a translation) into the app at the cursor, with a space before it if needed. */
    fun insertTranslation(text: String) {
        val ic = appConnection() ?: return
        val before = ic.getTextBeforeCursor(1, 0)
        val needsSpace = !before.isNullOrEmpty() && !before.last().isWhitespace()
        val shown = if (needsSpace) " $text" else text
        ic.commitText(shown, 1)
        if (!translateActive) recordInsert(shown)
    }

    /** Empties the translate box (the ✕ in the box). */
    fun clearTranslateText() {
        if (!translateActive) return
        composing.clear()
        candidates = emptyList()
        bestGuess = ""
        clearTranslateBox()
        updateAutoCaps()
    }

    private fun clearTranslateBox() {
        val box = translateConnection() ?: run { translateText = ""; return }
        box.finishComposingText()
        val before = box.getTextBeforeCursor(MAX_TRANSLATE * 2, 0)?.length ?: 0
        if (before > 0) box.deleteSurroundingText(before, 0)
        translateText = ""
    }

    private fun refreshTranslateText() {
        if (!translateActive) return
        translateText = translateConnection()?.getTextBeforeCursor(MAX_TRANSLATE, 0)?.toString().orEmpty()
    }

    // ---------------------------------------------------------------- voice

    /**
     * Shows recognised speech: [final] = false shows it as composing text (still changing),
     * true commits it. A space is added in front when the text before the cursor needs one.
     * In translate mode the words go into the translate box and get translated.
     */
    fun showVoiceText(text: String, final: Boolean) {
        val ic = inputConnection() ?: return
        if (!voiceComposing) {
            commitComposing()
            val before = ic.getTextBeforeCursor(1, 0)
            voiceNeedsSpace = !before.isNullOrEmpty() && !before.last().isWhitespace()
        }
        val shown = if (voiceNeedsSpace && text.isNotEmpty()) " $text" else text
        if (final) {
            if (shown.isEmpty()) ic.setComposingText("", 1) else ic.commitText(shown, 1)
            ic.finishComposingText()
            recordInsert(shown)
            voiceComposing = false
        } else {
            ic.setComposingText(shown, 1)
            voiceComposing = true
        }
        refreshTranslateText()
    }

    /** Keeps whatever voice text is showing (a key or panel change ends the voice phrase). */
    fun endVoiceText() {
        if (!voiceComposing) return
        inputConnection()?.finishComposingText()
        voiceComposing = false
    }

    // ---------------------------------------------------------------- media search

    /** Opens the search box in the media panel; keys now type into it. */
    fun startSearch() {
        commitComposing()
        searchActive = true
        searchQuery = submittedSearch.orEmpty()
        mode = KeyMode.LETTERS
        shift = ShiftState.OFF
    }

    /** Leaves typing mode. Keeps the last submitted search showing. */
    fun cancelSearch() {
        searchActive = false
        searchQuery = submittedSearch.orEmpty()
    }

    /** Clears the search (back to Trending). */
    fun clearSearch() {
        searchQuery = ""
        submittedSearch = null
    }

    /** Replaces the text in the search box (quick-search chips). Results update live. */
    fun setSearchText(text: String) {
        searchQuery = text.take(MAX_SEARCH)
    }

    /** Runs a search directly, e.g. from a category chip. */
    fun submitSearch(query: String?) {
        searchActive = false
        searchQuery = query.orEmpty()
        submittedSearch = query?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Handles a key while the search box has focus. Returns false for keys that work as usual. */
    private fun onSearchAction(action: KeyAction): Boolean {
        when (action) {
            is KeyAction.Text -> {
                val raw = action.value
                val text = if (shift != ShiftState.OFF && raw.length == 1 && raw[0].isLetter()) raw.uppercase() else raw
                if (searchQuery.length < MAX_SEARCH) searchQuery += text
                if (shift == ShiftState.ONCE) shift = ShiftState.OFF
            }
            KeyAction.Space -> if (searchQuery.isNotEmpty() && !searchQuery.endsWith(" ") && searchQuery.length < MAX_SEARCH) {
                searchQuery += " "
            }
            KeyAction.Delete -> searchQuery = searchQuery.dropLast(1)
            KeyAction.Enter -> submitSearch(searchQuery)
            KeyAction.ToggleLanguage -> Unit // search is in English letters
            else -> return false // shift, symbols pages
        }
        return true
    }

    /** Picks a fancy style (or null to turn it off) and goes back to the keys. */
    fun setFancy(style: FancyText.Style?) {
        fancyStyle = style?.takeIf { it.keyboardSafe }
        panel = Panel.KEYS
    }

    fun showKeys() {
        searchActive = false
        endVoiceText()
        panel = Panel.KEYS
    }

    // ---------------------------------------------------------------- tools

    /** Opens a tool panel (or the Tools hub) in place of the keys. */
    fun openPanel(target: Panel) {
        searchActive = false
        endVoiceText()
        commitComposing()
        panel = target
    }

    /**
     * For tools that edit the app's text (Text edit, Repeat): ends the current word first and
     * returns the app's editor (the translate box while translating).
     */
    fun toolConnection(): InputConnection? {
        endVoiceText()
        commitComposing()
        lastAutoCorrection = null
        return inputConnection()
    }

    /** Number pad and calculator: puts text in exactly as given (no fancy style, no Singlish). */
    fun insertRaw(text: String) {
        lastAutoCorrection = null
        endVoiceText()
        commitComposing()
        commit(text)
        refreshTranslateText()
    }

    /** Your shortcuts (code → text), for Phrase studio's "Mine". */
    fun shortcutList(): List<Pair<String, String>> = shortcuts().toList()

    private fun shortcutFor(typed: String): String? {
        if (!shortcutsEnabled || restrictedField || typed.isEmpty() || typed.length > MAX_SHORTCUT_KEY) return null
        return shortcuts()[typed.lowercase()]
    }

    /**
     * Space after a shortcut key ("gm") puts its text in instead, plus a space. Works in Sinhala
     * mode (the English letters you typed) and English mode (the word before the cursor).
     * Backspace straight after puts the key back, like undoing an auto-correction.
     */
    private fun expandShortcut(): Boolean {
        if (!shortcutsEnabled || restrictedField) return false
        val ic = inputConnection() ?: return false
        val typed = if (composing.isNotEmpty()) {
            composing.toString()
        } else {
            val before = ic.getTextBeforeCursor(MAX_SHORTCUT_KEY + 1, 0)?.toString() ?: return false
            before.takeLastWhile { it.isLetterOrDigit() && it.code < 128 }
        }
        val text = shortcutFor(typed) ?: return false
        if (composing.isNotEmpty()) {
            ic.commitText(text, 1) // replaces the composing word
            composing.clear()
            candidates = emptyList()
            bestGuess = ""
        } else {
            ic.deleteSurroundingText(typed.length, 0)
            ic.commitText(text, 1)
            // The typed code was logged letter by letter: take it back off the log.
            val top = undoLog.lastOrNull()
            if (top != null && top.endsWith(typed)) {
                val rest = top.dropLast(typed.length)
                if (rest.isEmpty()) undoLog.removeLast() else undoLog[undoLog.lastIndex] = rest
            }
        }
        recordInsert(text)
        commit(" ")
        lastAutoCorrection = text to typed
        return true
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
        if (searchActive) return null
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

    /** Where typing goes: the translate box while translating, otherwise the app. */
    private fun inputConnection(): InputConnection? =
        if (translateActive) translateConnection() else appConnection()

    /** Key events go to the app, except in the translate box, which is edited directly. */
    private fun sendKey(keyCode: Int) {
        if (!translateActive) {
            sendAppKey(keyCode)
            return
        }
        val box = translateConnection() ?: return
        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                val before = box.getTextBeforeCursor(2, 0)
                val n = if (before != null && before.length == 2 && Character.isSurrogatePair(before[0], before[1])) 2 else 1
                if (!before.isNullOrEmpty()) box.deleteSurroundingText(n, 0)
            }
            else -> Unit
        }
    }

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
            val style = fancyStyle
            commit(if (style != null && !restrictedField && c != null && c.isLetterOrDigit()) style.apply(text) else text)
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
        // A shortcut ("gm") goes first, because space expands it.
        val shortcut = shortcutFor(latin)
        candidates = if (livePreview) (listOfNotNull(shortcut) + list + latin).distinct() else emptyList()
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
        recordInsert(word)
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
        clearUndo()
        learn(original)
        updateAutoCaps()
        return true
    }

    private fun learn(word: String) {
        if (!learnWords || incognito || restrictedField || word.none { it in '\u0D80'..'\u0DFF' }) return
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
        clearUndo() // Backspace edits the text by hand; Undo only takes back whole pieces
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
        recordInsert(text)
    }

    /** Adds [text] to the undo log: letters join the current word; spaces / punctuation end it. */
    private fun recordInsert(text: String) {
        if (text.isEmpty()) return
        if (!redoing) redoLog.clear()
        val top = undoLog.lastOrNull()
        val joins = top != null && (
            text.isBlank() ||
                (text.length == 1 && !text[0].isWhitespace() && !top.last().isWhitespace())
            )
        if (joins) {
            undoLog[undoLog.lastIndex] = top + text
        } else {
            undoLog.addLast(text)
            while (undoLog.size > MAX_UNDO) undoLog.removeFirst()
        }
    }

    private fun clearUndo() {
        undoLog.clear()
        redoLog.clear()
    }

    /**
     * Undo: takes back the word being typed, or the last piece SinhalaX put in (word + space,
     * suggestion, emoji, paste, shortcut…). False when there is nothing it can safely undo
     * (e.g. the cursor moved); the caller can then try the app's own undo.
     */
    fun undo(): Boolean {
        endVoiceText()
        lastAutoCorrection = null
        val ic = inputConnection() ?: return false
        if (composing.isNotEmpty()) {
            ic.setComposingText("", 1)
            ic.finishComposingText()
            composing.clear()
            candidates = emptyList()
            bestGuess = ""
            updateAutoCaps()
            refreshTranslateText()
            return true
        }
        val entry = undoLog.lastOrNull() ?: return false
        if (ic.getTextBeforeCursor(entry.length, 0)?.toString() != entry) {
            clearUndo() // the text changed under us: don't delete the wrong thing
            return false
        }
        ic.deleteSurroundingText(entry.length, 0)
        undoLog.removeLast()
        redoLog.addLast(entry)
        updateAutoCaps()
        refreshTranslateText()
        return true
    }

    /** Redo: puts back what Undo took away. */
    fun redo(): Boolean {
        val entry = redoLog.removeLastOrNull() ?: return false
        endVoiceText()
        commitComposing()
        redoing = true
        commit(entry)
        redoing = false
        updateAutoCaps()
        refreshTranslateText()
        return true
    }

    val canRedo: Boolean get() = redoLog.isNotEmpty()

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
        const val MAX_SEARCH = 50

        /** Longest text the translate box holds (one request). */
        const val MAX_TRANSLATE = 1000

        /** How many pieces Undo remembers. */
        const val MAX_UNDO = 30

        /** Same limit as ShortcutRepository.MAX_KEY. */
        const val MAX_SHORTCUT_KEY = 20
        /** EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING (API 26), as a literal so it works on every version. */
        const val IME_FLAG_NO_PERSONALIZED_LEARNING = 0x1000000
    }
}

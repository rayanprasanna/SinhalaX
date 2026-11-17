package dev.valhalla.sinhalax.data

import android.content.Context
import dev.valhalla.sinhalax.engine.SinglishTransliterator
import dev.valhalla.sinhalax.prediction.Lexicon
import dev.valhalla.sinhalax.prediction.Predictor
import dev.valhalla.sinhalax.prediction.UserDictionary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns the dictionary and the learned words. One instance for the whole process
 * (shared by the keyboard and the app). Learned words are saved only to this phone.
 *
 * Threading: the user dictionary is touched only on the main thread; files are read/written on IO.
 */
class PredictionRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _predictor = MutableStateFlow<Predictor?>(null)

    /** null until the dictionary has loaded (about half a second on first start). */
    val predictor: StateFlow<Predictor?> = _predictor.asStateFlow()

    private val _learnedWords = MutableStateFlow<List<UserDictionary.Entry>>(emptyList())
    val learnedWords: StateFlow<List<UserDictionary.Entry>> = _learnedWords.asStateFlow()

    private val file = File(context.filesDir, "learned_words.tsv")
    private var user: UserDictionary? = null
    private var loadStarted = false
    private var saveJob: Job? = null

    fun ensureLoaded() {
        if (loadStarted) return
        loadStarted = true
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val lexicon = runCatching {
                    context.assets.open(DICTIONARY_ASSET).bufferedReader().useLines { Lexicon.parse(it) }
                }.getOrElse { Lexicon.EMPTY }
                val learned = runCatching {
                    if (file.exists()) UserDictionary.parse(file.readText()) else UserDictionary()
                }.getOrElse { UserDictionary() }
                val predictor = Predictor(SinglishTransliterator(), lexicon, learned)
                predictor.predict("kohomadha") // warm up so the first real keystroke is fast
                learned to predictor
            }
            user = loaded.first
            _predictor.value = loaded.second
            publish()
        }
    }

    fun learn(word: String) {
        val dict = user ?: return
        dict.learn(word)
        scheduleSave()
    }

    fun remove(word: String) {
        val dict = user ?: return
        dict.remove(word)
        publish()
        scheduleSave()
    }

    fun clear() {
        val dict = user ?: return
        dict.clear()
        publish()
        scheduleSave()
    }

    private fun publish() {
        _learnedWords.value = user?.all().orEmpty()
    }

    /** Batches writes: saves 2 s after the last change. */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(2_000)
            val text = user?.serialize() ?: return@launch
            publish()
            withContext(Dispatchers.IO) {
                runCatching {
                    val tmp = File(file.parentFile, file.name + ".tmp")
                    tmp.writeText(text)
                    if (!tmp.renameTo(file)) {
                        file.writeText(text)
                        tmp.delete()
                    }
                }
            }
        }
    }

    companion object {
        const val DICTIONARY_ASSET = "dict/si_words.txt"
    }
}

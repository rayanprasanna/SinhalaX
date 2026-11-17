package dev.valhalla.sinhalax.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.clipStore: DataStore<Preferences> by preferencesDataStore(name = "clipboard_history")

/** Clipboard history, stored only on the device. Newest first, no duplicates. */
class ClipboardRepository(context: Context) {

    private val store = context.applicationContext.clipStore
    private val key = stringPreferencesKey("items")

    val items: Flow<List<String>> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { decode(it[key]) }

    suspend fun add(text: String) {
        store.edit { p ->
            val current = decode(p[key])
            p[key] = encode((listOf(text) + current.filterNot { it == text }).take(MAX_ITEMS))
        }
    }

    suspend fun remove(text: String) {
        store.edit { p -> p[key] = encode(decode(p[key]).filterNot { it == text }) }
    }

    suspend fun clear() {
        store.edit { it.remove(key) }
    }

    private fun decode(raw: String?): List<String> =
        raw?.split(SEPARATOR)?.filter { it.isNotEmpty() } ?: emptyList()

    private fun encode(list: List<String>): String = list.joinToString(SEPARATOR)

    companion object {
        const val MAX_ITEMS = 30
        private const val SEPARATOR = "\u001E"
    }
}

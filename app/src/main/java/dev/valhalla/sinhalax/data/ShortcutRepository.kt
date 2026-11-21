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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.shortcutStore: DataStore<Preferences> by preferencesDataStore(name = "shortcuts")

/** One text expansion: type [key] then space, and it becomes [text]. */
data class Shortcut(val key: String, val text: String)

/**
 * Shortcuts (text expansion), stored only on this phone. Keys are letters/digits, matched
 * case-insensitively, against what you typed in English letters (also in Sinhala mode: "gm").
 * A few Sinhala examples are added the first time so people can see how it works.
 */
class ShortcutRepository(context: Context) {

    private val store = context.applicationContext.shortcutStore
    private val itemsKey = stringPreferencesKey("items")

    val items: Flow<List<Shortcut>> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p -> p[itemsKey]?.let(::decode) ?: DEFAULTS }

    /** Adds or replaces (same key). */
    suspend fun put(shortcut: Shortcut, replacingKey: String? = null) {
        val key = normalizeKey(shortcut.key)
        if (key.isEmpty() || shortcut.text.isBlank()) return
        store.edit { p ->
            val current = p[itemsKey]?.let(::decode) ?: DEFAULTS
            val drop = setOfNotNull(key, replacingKey?.let(::normalizeKey))
            val next = listOf(Shortcut(key, shortcut.text.trim())) + current.filterNot { normalizeKey(it.key) in drop }
            p[itemsKey] = encode(next.take(MAX_ITEMS))
        }
    }

    suspend fun remove(key: String) {
        val k = normalizeKey(key)
        store.edit { p ->
            val current = p[itemsKey]?.let(::decode) ?: DEFAULTS
            p[itemsKey] = encode(current.filterNot { normalizeKey(it.key) == k })
        }
    }

    private fun decode(raw: String): List<Shortcut> = runCatching {
        val a = JSONArray(raw)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val k = normalizeKey(o.optString("k"))
            val t = o.optString("t")
            if (k.isEmpty() || t.isBlank()) null else Shortcut(k, t)
        }
    }.getOrDefault(emptyList())

    private fun encode(list: List<Shortcut>): String =
        JSONArray().apply { list.forEach { put(JSONObject().put("k", it.key).put("t", it.text)) } }.toString()

    companion object {
        const val MAX_ITEMS = 200
        const val MAX_KEY = 20

        /** Letters and digits only, lower case. */
        fun normalizeKey(raw: String): String =
            raw.trim().lowercase().filter { it.isLetterOrDigit() && it.code < 128 }.take(MAX_KEY)

        val DEFAULTS = listOf(
            Shortcut("gm", "සුභ උදෑසනක්! ☀️"),
            Shortcut("gn", "සුභ රාත්‍රියක්! 🌙"),
            Shortcut("ty", "බොහොම ස්තූතියි! 🙏"),
            Shortcut("hbd", "සුභ උපන්දිනයක්! 🎂🎉"),
        )
    }
}

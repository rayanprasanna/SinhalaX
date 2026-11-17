package dev.valhalla.sinhalax.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Read-only client for the public Audio Hub endpoints. Used only by the app's theme store,
 * never by the keyboard service. No library needed: HttpURLConnection + org.json.
 */
object StoreApi {

    const val BASE_URL = "https://apps.zentiumdev.com/audiohub"

    /** Audio Hub category for keypress sounds. Upload short click sounds with exactly this category. */
    const val SOUND_CATEGORY = "Keyboard Sounds"

    data class Page<T>(val items: List<T>, val page: Int, val totalPages: Int, val totalItems: Long)

    data class Category(val name: String, val count: Long)

    data class RemoteBackground(
        val id: String,
        val title: String,
        val category: String?,
        val contentType: String,
        val sha256: String,
        val dominantColor: String,
        val keyTextColor: String,
        val imageUrl: String,
        val thumbUrl: String,
    )

    data class RemoteSound(
        val id: String,
        val title: String,
        val description: String?,
        val durationMs: Long?,
        val contentType: String,
        val sizeBytes: Long,
        val streamUrl: String,
    )

    suspend fun backgrounds(page: Int, category: String?, size: Int = 24): Page<RemoteBackground> {
        val query = buildString {
            append("/backgrounds?page=").append(page).append("&size=").append(size)
            if (!category.isNullOrBlank()) append("&category=").append(encode(category))
        }
        return page(getJson(query)) { o ->
            RemoteBackground(
                id = o.getString("id"),
                title = o.getString("title"),
                category = o.optStringOrNull("category"),
                contentType = o.optString("contentType", "image/jpeg"),
                sha256 = o.getString("sha256"),
                dominantColor = o.optString("dominantColor", "#333333"),
                keyTextColor = o.optString("keyTextColor", "#FFFFFF"),
                imageUrl = o.getString("imageUrl"),
                thumbUrl = o.getString("thumbUrl"),
            )
        }
    }

    suspend fun backgroundCategories(): List<Category> {
        val array = JSONArray(getText("/background-categories"))
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Category(o.getString("name"), o.optLong("count"))
        }
    }

    suspend fun soundPacks(page: Int = 1, size: Int = 50): Page<RemoteSound> {
        val query = "/audios?category=${encode(SOUND_CATEGORY)}&sort=title&page=$page&size=$size"
        return page(getJson(query)) { o ->
            RemoteSound(
                id = o.getString("id"),
                title = o.getString("title"),
                description = o.optStringOrNull("description"),
                durationMs = if (o.isNull("durationMs")) null else o.optLong("durationMs"),
                contentType = o.optString("contentType", "audio/mpeg"),
                sizeBytes = o.optLong("sizeBytes"),
                streamUrl = o.getString("streamUrl"),
            )
        }
    }

    /** Downloads [url] to [target] atomically (temp file, then rename). */
    suspend fun download(url: String, target: File, maxBytes: Long = 30L * 1024 * 1024) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".part")
        val connection = open(url)
        try {
            if (connection.responseCode !in 200..299) throw IOException("Download failed (${connection.responseCode})")
            var total = 0L
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) throw IOException("File is too large")
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } catch (e: Exception) {
            temp.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    // ------------------------------------------------------------------ internals

    private fun <T> page(json: JSONObject, map: (JSONObject) -> T): Page<T> {
        val items = json.getJSONArray("items")
        return Page(
            items = (0 until items.length()).map { map(items.getJSONObject(it)) },
            page = json.optInt("page", 1),
            totalPages = json.optInt("totalPages", 1),
            totalItems = json.optLong("totalItems"),
        )
    }

    private suspend fun getJson(path: String): JSONObject = JSONObject(getText(path))

    private suspend fun getText(path: String): String = withContext(Dispatchers.IO) {
        val connection = open(BASE_URL + path)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("Server returned $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json, image/*, audio/*")
            setRequestProperty("User-Agent", "SinhalaX-Android")
        }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
}

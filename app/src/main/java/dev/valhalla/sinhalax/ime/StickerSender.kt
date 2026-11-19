package dev.valhalla.sinhalax.ime

import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import dev.valhalla.sinhalax.store.KlipyApi
import java.io.File

/**
 * Sends GIFs, stickers and memes into the app the user is typing in, using Android's
 * rich-content API (the same way Gboard sends GIFs and stickers to WhatsApp, Messenger, Telegram ...).
 */
object StickerSender {

    enum class Result { SENT, NOT_SUPPORTED, FAILED }

    fun authority(context: Context) = "${context.packageName}.stickers"

    /** True if the current text field accepts images at all. */
    fun canSend(info: EditorInfo?): Boolean =
        info != null && EditorInfoCompat.getContentMimeTypes(info).any { ClipDescription.compareMimeTypes(it, "image/*") || it.startsWith("image/") }

    suspend fun send(
        context: Context,
        info: EditorInfo?,
        connection: InputConnection?,
        media: KlipyApi.Media,
    ): Result {
        if (info == null || connection == null) return Result.FAILED
        val mime = pickFormat(info, media.sendOrder) ?: return Result.NOT_SUPPORTED
        return try {
            val file = KlipyApi.downloadForSending(context, media.files.getValue(mime), media.cacheKey, KlipyApi.extensionFor(mime))
            commit(context, info, connection, file, mime, media.title)
        } catch (e: Exception) {
            Result.FAILED
        }
    }

    /** Sends an already-saved file. */
    fun sendFile(context: Context, info: EditorInfo?, connection: InputConnection?, file: File, mime: String, label: String): Result {
        if (info == null || connection == null) return Result.FAILED
        if (pickFormat(info, listOf(mime)) == null) return Result.NOT_SUPPORTED
        return try {
            commit(context, info, connection, file, mime, label)
        } catch (e: Exception) {
            Result.FAILED
        }
    }

    private fun commit(context: Context, info: EditorInfo, connection: InputConnection, file: File, mime: String, label: String): Result {
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        val content = InputContentInfoCompat(uri, ClipDescription(label.ifBlank { "Image" }, arrayOf(mime)), null)
        var flags = 0
        if (Build.VERSION.SDK_INT >= 25) {
            flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
        } else {
            context.grantUriPermission(info.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return if (InputConnectionCompat.commitContent(connection, info, content, flags, null)) Result.SENT else Result.NOT_SUPPORTED
    }

    private fun pickFormat(info: EditorInfo, candidates: List<String>): String? {
        val accepted = EditorInfoCompat.getContentMimeTypes(info)
        if (accepted.isEmpty()) return null
        return candidates.firstOrNull { mime -> accepted.any { ClipDescription.compareMimeTypes(mime, it) } }
    }
}

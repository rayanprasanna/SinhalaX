package dev.valhalla.sinhalax.store

import android.content.Context
import dev.valhalla.sinhalax.data.SettingsRepository
import java.io.File

/**
 * Downloads store items into the app's private storage and switches the keyboard to them.
 * Only one background and one sound pack are kept on the phone at a time.
 */
class StoreRepository(context: Context, private val settings: SettingsRepository) {

    private val dir = File(context.filesDir, "store")

    suspend fun applyBackground(bg: StoreApi.RemoteBackground) {
        val ext = if (bg.contentType == "image/png") "png" else "jpg"
        val target = File(dir, "background_${bg.sha256.take(16)}.$ext")
        if (!target.exists()) StoreApi.download(bg.imageUrl, target)
        settings.update {
            it.copy(
                backgroundId = bg.id,
                backgroundPath = target.path,
                backgroundKeyTextColor = bg.keyTextColor,
                backgroundDominantColor = bg.dominantColor,
            )
        }
        deleteOthers("background_", keep = target)
    }

    suspend fun removeBackground() {
        settings.update {
            it.copy(backgroundId = "", backgroundPath = "", backgroundKeyTextColor = "", backgroundDominantColor = "")
        }
        deleteOthers("background_", keep = null)
    }

    /** Downloads (if needed) and selects the sound. Returns the local file path. */
    suspend fun applySound(sound: StoreApi.RemoteSound): String {
        val target = File(dir, "sound_${sound.id}.${extensionFor(sound.contentType)}")
        if (!target.exists()) StoreApi.download(sound.streamUrl, target, maxBytes = 2L * 1024 * 1024)
        settings.update { it.copy(soundPackId = sound.id, soundPackPath = target.path, sound = true) }
        deleteOthers("sound_", keep = target)
        return target.path
    }

    suspend fun useSystemSound() {
        settings.update { it.copy(soundPackId = "", soundPackPath = "") }
        deleteOthers("sound_", keep = null)
    }

    private fun deleteOthers(prefix: String, keep: File?) {
        dir.listFiles { f -> f.name.startsWith(prefix) && f != keep }?.forEach { it.delete() }
    }

    private fun extensionFor(contentType: String): String = when {
        "ogg" in contentType || "opus" in contentType -> "ogg"
        "wav" in contentType -> "wav"
        "mp4" in contentType || "m4a" in contentType || "aac" in contentType -> "m4a"
        "flac" in contentType -> "flac"
        else -> "mp3"
    }
}

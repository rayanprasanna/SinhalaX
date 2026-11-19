package dev.valhalla.sinhalax.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Invisible screen that asks for the microphone permission. A keyboard can't show permission
 * dialogs itself, so the voice panel opens this, and it closes as soon as you answer.
 */
class VoicePermissionActivity : ComponentActivity() {

    private val request = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when {
            granted -> Toast.makeText(this, "Microphone allowed. Tap the mic in the keyboard to speak.", Toast.LENGTH_LONG).show()
            !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                // "Don't ask again" (or blocked): the only way left is the app's settings page.
                Toast.makeText(this, "Turn on Microphone for SinhalaX in Permissions.", Toast.LENGTH_LONG).show()
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            else -> Toast.makeText(this, "Voice typing needs the microphone.", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            finish()
            return
        }
        if (savedInstanceState == null) request.launch(Manifest.permission.RECORD_AUDIO)
    }

    companion object {
        fun launch(context: Context) {
            runCatching {
                context.startActivity(
                    Intent(context, VoicePermissionActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
                )
            }
        }
    }
}

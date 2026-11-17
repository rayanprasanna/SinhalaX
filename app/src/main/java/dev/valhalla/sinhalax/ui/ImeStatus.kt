package dev.valhalla.sinhalax.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

data class ImeStatus(val enabled: Boolean, val selected: Boolean) {
    val ready: Boolean get() = enabled && selected

    companion object {
        fun read(context: Context): ImeStatus {
            val imm = context.getSystemService(InputMethodManager::class.java)
            val enabled = imm?.enabledInputMethodList.orEmpty().any { it.packageName == context.packageName }
            val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            val selected = current?.startsWith(context.packageName + "/") == true
            return ImeStatus(enabled, selected)
        }

        fun openEnableSettings(context: Context) {
            context.startActivity(
                Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        fun showPicker(context: Context) {
            context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }
    }
}

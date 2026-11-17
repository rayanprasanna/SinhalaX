package dev.valhalla.sinhalax.ime

import dev.valhalla.sinhalax.keyboard.StickerOutcome
import dev.valhalla.sinhalax.store.KlipyApi
import android.content.ClipboardManager
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.valhalla.sinhalax.MainActivity
import dev.valhalla.sinhalax.SinhalaXApp
import dev.valhalla.sinhalax.data.KeyboardSettings
import dev.valhalla.sinhalax.engine.SinglishTransliterator
import dev.valhalla.sinhalax.keyboard.KeyboardController
import dev.valhalla.sinhalax.keyboard.SinhalaXKeyboard
import kotlinx.coroutines.launch

/**
 * The input method. Hosts the Compose keyboard, so it provides the lifecycle,
 * ViewModel store and saved-state owners Compose expects from an Activity.
 *
 * Privacy: the only network use here is the GIF / sticker panel (KLIPY), and only while it is open. What you type is never
 * sent anywhere. Only finished Sinhala words are
 * counted in an on-device personal dictionary, which the user can view, delete or turn off.
 */
class SinhalaXImeService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val app: SinhalaXApp get() = application as SinhalaXApp

    private lateinit var controller: KeyboardController
    private val settingsState = mutableStateOf(KeyboardSettings())
    private val clipItems = mutableStateOf<List<String>>(emptyList())
    private val canSendSticker = mutableStateOf(false)

    private val clipboardManager: ClipboardManager by lazy { getSystemService(ClipboardManager::class.java) }
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { onClipChanged() }

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        app.prediction.ensureLoaded()
        controller = KeyboardController(
            engine = SinglishTransliterator(),
            inputConnection = { currentInputConnection },
            sendKey = { keyCode -> sendDownUpKeyEvents(keyCode) },
            predictor = { app.prediction.predictor.value },
            onWordLearned = { word -> app.prediction.learn(word) },
        )

        lifecycleScope.launch {
            app.settings.settings.collect { settings ->
                settingsState.value = settings
                controller.applySettings(settings)
            }
        }
        lifecycleScope.launch {
            app.clipboard.items.collect { clipItems.value = it }
        }
        clipboardManager.addPrimaryClipChangedListener(clipListener)

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onCreateInputView(): View {
        // Compose looks for these owners on the window's root view.
        window?.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@SinhalaXImeService)
            setViewTreeViewModelStoreOwner(this@SinhalaXImeService)
            setViewTreeSavedStateRegistryOwner(this@SinhalaXImeService)
            setContent {
                SinhalaXKeyboard(
                    controller = controller,
                    settings = settingsState.value,
                    clipItems = clipItems.value,
                    onOpenApp = ::openApp,
                    onHide = { requestHideSelf(0) },
                    onDeleteClip = { text -> lifecycleScope.launch { app.clipboard.remove(text) } },
                    onClearClips = { lifecycleScope.launch { app.clipboard.clear() } },
                    canSendSticker = canSendSticker.value,
                    onSendSticker = ::sendSticker,
                )
            }
        }
    }

    /** Never switch to the full-screen extract editor in landscape. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        controller.onStartInput(attribute)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        controller.onStartInputView()
        canSendSticker.value = StickerSender.canSend(info)
    }

    /** Sends a KLIPY GIF / sticker / meme into the app being typed in. Network is used only for this tap. */
    private suspend fun sendSticker(media: KlipyApi.Media, query: String?): StickerOutcome {
        controller.commitComposing()
        return when (StickerSender.send(this, currentInputEditorInfo, currentInputConnection, media)) {
            StickerSender.Result.SENT -> {
                lifecycleScope.launch { KlipyApi.reportShare(this@SinhalaXImeService, media.kind, media.slug, query) }
                StickerOutcome.SENT
            }
            StickerSender.Result.NOT_SUPPORTED -> StickerOutcome.NOT_SUPPORTED
            StickerSender.Result.FAILED -> StickerOutcome.FAILED
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        controller.commitComposing()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        controller.reset()
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        controller.onSelectionChanged(newSelStart, newSelEnd, candidatesEnd)
    }

    override fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(clipListener)
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDestroy()
    }

    /**
     * Any crash in this process makes Android drop SinhalaX and fall back to the default keyboard,
     * so this runs fully guarded. coerceToText() can throw (e.g. SecurityException) when another app
     * copies an image or file link, which is exactly what used to happen here.
     */
    private fun onClipChanged() {
        try {
            if (!settingsState.value.clipboardHistory || controller.restrictedField) return
            val clip = clipboardManager.primaryClip ?: return
            // Password managers and banking apps mark copied secrets as sensitive.
            if (clip.description?.extras?.getBoolean(EXTRA_IS_SENSITIVE) == true) return
            if (clip.itemCount == 0) return
            val item = clip.getItemAt(0)
            val text = (item.text ?: if (item.uri == null) item.coerceToText(this) else null)
                ?.toString()?.trim().orEmpty()
            if (text.isEmpty() || text.length > 5_000) return
            lifecycleScope.launch { runCatching { app.clipboard.add(text) } }
        } catch (t: Throwable) {
            android.util.Log.w("SinhalaX", "Clipboard read skipped", t)
        }
    }

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private companion object {
        /** Same value as ClipDescription.EXTRA_IS_SENSITIVE (API 33), usable on every version. */
        const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    }
}

package com.nacham.callnotes.call

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.nacham.callnotes.audio.RecorderService
import com.nacham.callnotes.util.SettingsStore

/**
 * Detects VoIP calls (WhatsApp/Telegram/etc.) by watching which app is in the foreground and
 * whether the audio system is in communication mode. Cheap and robust enough for v1.
 */
class CallDetectorService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var recording = false
    private var activePkg: String? = null

    private val stopCheck = Runnable {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (am.mode != AudioManager.MODE_IN_COMMUNICATION) stopIfNeeded()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in CallEvents.VOIP_PACKAGES) {
            // Left a VoIP app — verify the call really ended via audio mode (debounced).
            if (recording) handler.postDelayed(stopCheck, DEBOUNCE_MS)
            return
        }
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (am.mode == AudioManager.MODE_IN_COMMUNICATION && !recording) {
            if (!SettingsStore.isEnabled(this)) return
            recording = true
            activePkg = pkg
            RecorderService.start(this, CallSource.VOIP.name, contactFor(pkg))
        }
        // TODO(handoff): for apps that don't flip MODE_IN_COMMUNICATION immediately, also look for
        // an "End call" / call-duration node in the window to confirm an active call.
    }

    private fun stopIfNeeded() {
        if (recording) {
            recording = false
            activePkg = null
            RecorderService.stop(this)
        }
    }

    private fun contactFor(pkg: String): String = when {
        pkg.startsWith("com.whatsapp") -> "WhatsApp call"
        pkg.startsWith("org.telegram") || pkg.startsWith("org.thunderdog") -> "Telegram call"
        else -> "VoIP call"
    }

    override fun onInterrupt() {}

    companion object { private const val DEBOUNCE_MS = 1500L }
}

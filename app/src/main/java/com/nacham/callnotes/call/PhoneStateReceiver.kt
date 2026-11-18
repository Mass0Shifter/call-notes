package com.nacham.callnotes.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.nacham.callnotes.audio.RecorderService
import com.nacham.callnotes.util.SettingsStore

/**
 * Cellular call detection. Note: on a non-root S23 the mic only captures the user's side well;
 * for the far side of GSM calls, rely on Samsung's built-in recorder + CallImport (see HANDOFF).
 * This still gives a timestamped recording of the user's half immediately.
 */
class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        if (!SettingsStore.isEnabled(ctx) || !SettingsStore.recordCellularMic(ctx)) return
        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_OFFHOOK ->
                RecorderService.start(ctx, CallSource.CELLULAR.name, "Phone call")
            TelephonyManager.EXTRA_STATE_IDLE ->
                RecorderService.stop(ctx)
        }
    }
}

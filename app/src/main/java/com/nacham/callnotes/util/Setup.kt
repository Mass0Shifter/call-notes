package com.nacham.callnotes.util

import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.nacham.callnotes.R
import com.nacham.callnotes.call.CallDetectorService
import com.nacham.callnotes.call.RecordTileService

/** Checks + deep-links for the one-time setup the user must do manually. */
object Setup {

    fun hasMic(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun hasNotifications(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        else true

    fun hasApiKey(ctx: Context): Boolean = SettingsStore.apiKey(ctx).isNotBlank()

    /** Reads the system list of enabled accessibility services and looks for ours. */
    fun isAccessibilityOn(ctx: Context): Boolean {
        val expected = ComponentName(ctx, CallDetectorService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun openAccessibilitySettings(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openAppNotificationSettings(ctx: Context) {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    /** Core setup is enough to prove the app works manually; accessibility is for VoIP auto-record. */
    fun coreComplete(ctx: Context): Boolean = hasMic(ctx) && hasApiKey(ctx)

    /** Android 13+ lets us prompt the user to add the QS tile without hunting in the shade. */
    fun requestAddTile(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val sbm = activity.getSystemService(StatusBarManager::class.java)
            sbm.requestAddTileService(
                ComponentName(activity, RecordTileService::class.java),
                activity.getString(R.string.tile_label),
                Icon.createWithResource(activity, R.drawable.ic_record),
                { it.run() },
                { /* result code; ignore */ }
            )
        }
        // On Android 12 and below there's no add-tile API; the user adds it from the
        // Quick Settings shade's edit screen (covered by the onboarding tip).
    }
}

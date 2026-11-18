package com.nacham.callnotes

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class CallNotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_RECORDING,
                    "Recording",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Shown while a call is being recorded" }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_PROCESSING,
                    "Processing",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Transcription and notes progress" }
            )
        }
    }

    companion object {
        const val CHANNEL_RECORDING = "recording"
        const val CHANNEL_PROCESSING = "processing"
    }
}

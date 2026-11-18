package com.nacham.callnotes.call

object CallEvents {
    const val ACTION_START = "com.nacham.callnotes.START"
    const val ACTION_STOP = "com.nacham.callnotes.STOP"
    const val ACTION_TOGGLE = "com.nacham.callnotes.TOGGLE"
    const val EXTRA_SOURCE = "source"
    const val EXTRA_CONTACT = "contact"

    /** Apps whose in-call audio we attempt to capture via the microphone. */
    val VOIP_PACKAGES = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.thunderdog.challegram",   // Telegram X
        "com.google.android.apps.tachyon", // Google Meet/Duo
        "com.skype.raider",
        "com.viber.voip",
        "us.zoom.videomeetings",
        "com.microsoft.teams"
    )
}

enum class CallSource { CELLULAR, VOIP, MANUAL }

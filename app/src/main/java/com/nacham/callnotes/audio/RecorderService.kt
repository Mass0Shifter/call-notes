package com.nacham.callnotes.audio

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nacham.callnotes.CallNotesApp
import com.nacham.callnotes.R
import com.nacham.callnotes.call.CallEvents
import com.nacham.callnotes.data.ProcessStatus
import com.nacham.callnotes.data.Recording
import com.nacham.callnotes.data.RecordingStore
import com.nacham.callnotes.ui.MainActivity
import java.io.File
import java.util.UUID

/**
 * Foreground service (type=microphone) that owns the MediaRecorder for one call.
 * Started by CallDetectorService (VoIP), PhoneStateReceiver (cellular), or RecordTileService (manual).
 */
class RecorderService : Service() {

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startedAt: Long = 0L
    private var source: String = "MANUAL"
    private var contact: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            CallEvents.ACTION_STOP -> { stopRecording(); stopSelf() }
            else -> {
                source = intent?.getStringExtra(CallEvents.EXTRA_SOURCE) ?: "MANUAL"
                contact = intent?.getStringExtra(CallEvents.EXTRA_CONTACT)
                startRecording()
            }
        }
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val tapIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RecorderService::class.java).setAction(CallEvents.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = NotificationCompat.Builder(this, CallNotesApp.CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_record)
            .setContentTitle(getString(R.string.recording_title))
            .setContentText(getString(R.string.recording_text, source))
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .addAction(R.drawable.ic_record, getString(R.string.stop), stop)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun startRecording() {
        if (recorder != null) return
        try {
            startForegroundCompat()
        } catch (e: Exception) {
            // Background-start of a mic FGS was refused. TODO(handoff): post a full-screen
            // "Tap to record" notification so the user can start it in the foreground instead.
            stopSelf(); return
        }
        val file = AudioStore.newFile(this, source)
        currentFile = file
        startedAt = System.currentTimeMillis()

        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this)
        else @Suppress("DEPRECATION") MediaRecorder()
        rec.apply {
            // MIC picks up earpiece/loudspeaker; best on speakerphone for VoIP.
            // VOICE_COMMUNICATION can apply echo cancellation that sometimes drops the far side,
            // so MIC is the default. TODO(handoff): expose source choice in Settings.
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(96_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = rec
    }

    private fun stopRecording() {
        val rec = recorder ?: return
        recorder = null
        val ok = runCatching { rec.stop() }.isSuccess
        runCatching { rec.release() }
        val file = currentFile ?: return
        if (ok && file.exists() && file.length() > 0) {
            RecordingStore.get(this).upsert(
                Recording(
                    id = UUID.randomUUID().toString(),
                    filePath = file.absolutePath,
                    source = source,
                    contact = contact,
                    startedAt = startedAt,
                    durationMs = System.currentTimeMillis() - startedAt,
                    status = ProcessStatus.RAW
                )
            )
        }
        currentFile = null
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 42

        fun start(ctx: Context, source: String, contact: String? = null) {
            val i = Intent(ctx, RecorderService::class.java)
                .putExtra(CallEvents.EXTRA_SOURCE, source)
                .putExtra(CallEvents.EXTRA_CONTACT, contact)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, RecorderService::class.java).setAction(CallEvents.ACTION_STOP))
        }
    }
}

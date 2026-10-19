package com.chiara.beep

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import java.util.Calendar
import kotlin.math.sin

class BeepService : Service() {

    companion object {
        const val CHANNEL_ID      = "chiara_beep_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP     = "ACTION_STOP"
        const val ACTION_REMINDER = "ACTION_REMINDER"
        var isRunning = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastBeepMinute = -1
    private lateinit var prefs: PrefsHelper
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationManager: NotificationManager

    // Tick setiap 10 detik: update notifikasi jam live + cek beep
    private val tickRunnable = object : Runnable {
        override fun run() {
            updateLiveNotification()
            val cal = Calendar.getInstance()
            val currentMinute = cal.get(Calendar.MINUTE)
            val currentSecond = cal.get(Calendar.SECOND)
            if (currentSecond == 0 && currentMinute != lastBeepMinute) {
                lastBeepMinute = currentMinute
                if (shouldBeep(cal)) {
                    Thread { playBeeps() }.start()
                }
            }
            handler.postDelayed(this, 10_000)
        }
    }

    // Tick cepat setiap 500ms hanya untuk deteksi pergantian menit
    private val beepCheckRunnable = object : Runnable {
        override fun run() {
            val cal = Calendar.getInstance()
            val currentMinute = cal.get(Calendar.MINUTE)
            val currentSecond = cal.get(Calendar.SECOND)
            if (currentSecond == 0 && currentMinute != lastBeepMinute) {
                lastBeepMinute = currentMinute
                if (shouldBeep(cal)) {
                    Thread { playBeeps() }.start()
                }
                updateLiveNotification()
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        prefs = PrefsHelper(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Chiara::BeepWakeLock"
        ).also { it.acquire(10 * 60 * 60 * 1000L) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                prefs.serviceOn = false
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REMINDER -> {
                Thread { playBeeps() }.start()
                return START_NOT_STICKY
            }
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        handler.post(beepCheckRunnable)
        handler.post(tickRunnable)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartIntent = Intent(applicationContext, BeepService::class.java)
        val pi = PendingIntent.getService(
            applicationContext, 1, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, pi)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(beepCheckRunnable)
        handler.removeCallbacks(tickRunnable)
        wakeLock?.release()
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Update notifikasi dengan jam saat ini
    private fun updateLiveNotification() {
        val cal = Calendar.getInstance()
        val h   = cal.get(Calendar.HOUR_OF_DAY)
        val m   = cal.get(Calendar.MINUTE)
        val timeStr = "${pad(h)}:${pad(m)}"
        notificationManager.notify(NOTIFICATION_ID, buildNotification(timeStr))
    }

    private fun shouldBeep(cal: Calendar): Boolean {
        if (prefs.dndRespect) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.isNotificationPolicyAccessGranted) {
                val filter = nm.currentInterruptionFilter
                if (filter == NotificationManager.INTERRUPTION_FILTER_NONE ||
                    filter == NotificationManager.INTERRUPTION_FILTER_ALARMS) {
                    return false
                }
            }
        }

        if (prefs.silentOn) {
            val nowH = cal.get(Calendar.HOUR_OF_DAY)
            val nowM = cal.get(Calendar.MINUTE)
            val nowTotal   = nowH * 60 + nowM
            val (sH, sM)   = prefs.silentStart.split(":").map { it.toInt() }
            val (eH, eM)   = prefs.silentEnd.split(":").map { it.toInt() }
            val startTotal = sH * 60 + sM
            val endTotal   = eH * 60 + eM
            val inSilent   = if (startTotal <= endTotal) {
                nowTotal in startTotal until endTotal
            } else {
                nowTotal >= startTotal || nowTotal < endTotal
            }
            if (inSilent) return false
        }

        return true
    }

    fun playBeeps() {
        val count    = prefs.beepCount
        val freq     = prefs.beepFreq.toDouble()
        val duration = prefs.beepDuration
        repeat(count) { i ->
            if (i > 0) Thread.sleep(90)
            playTone(freq, duration, 0.7f)
        }
    }

    private fun playTone(freqHz: Double, durationMs: Int, volume: Float) {
        val sampleRate = 44100
        val numSamples = sampleRate * durationMs / 1000
        val samples    = ShortArray(numSamples)
        val fadeLen    = minOf(numSamples / 4, sampleRate / 100)

        for (i in 0 until numSamples) {
            var env = 1.0
            if (i < fadeLen) env = i.toDouble() / fadeLen
            else if (i > numSamples - fadeLen) env = (numSamples - i).toDouble() / fadeLen
            val angle = 2.0 * Math.PI * i * freqHz / sampleRate
            samples[i] = (sin(angle) * env * volume * Short.MAX_VALUE).toInt().toShort()
        }

        val audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            samples.size * 2,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        try {
            audioTrack.write(samples, 0, samples.size)
            audioTrack.play()
            Thread.sleep(durationMs.toLong() + 30)
        } finally {
            audioTrack.stop()
            audioTrack.release()
        }
    }

    private fun buildNotification(timeStr: String? = null): Notification {
        val cal = Calendar.getInstance()
        val h   = cal.get(Calendar.HOUR_OF_DAY)
        val m   = cal.get(Calendar.MINUTE)
        val displayTime = timeStr ?: "${pad(h)}:${pad(m)}"

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, BeepService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Chiara  •  $displayTime")
            .setContentText("Beeping every minute")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun pad(n: Int) = n.toString().padStart(2, '0')
}

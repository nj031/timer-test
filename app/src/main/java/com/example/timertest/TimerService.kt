package com.example.timertest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

data class TimerUiState(
    val isRunning: Boolean = false,
    val totalMillis: Long = 0L,
    val remainingMillis: Long = 0L
)

class TimerService : LifecycleService() {

    private var countdownJob: Job? = null

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val durationMillis = intent.getLongExtra(EXTRA_DURATION_MILLIS, 0L)
                if (durationMillis > 0L) {
                    startCountdown(durationMillis)
                }
            }
            ACTION_STOP -> stopCountdown()
        }
        return START_NOT_STICKY
    }

    private fun startCountdown(durationMillis: Long) {
        countdownJob?.cancel()
        _state.value = TimerUiState(isRunning = true, totalMillis = durationMillis, remainingMillis = durationMillis)
        startForeground(NOTIFICATION_ID, buildNotification(durationMillis))

        countdownJob = lifecycleScope.launch {
            var remaining = durationMillis
            while (isActive && remaining > 0L) {
                delay(TICK_INTERVAL_MILLIS)
                remaining = (remaining - TICK_INTERVAL_MILLIS).coerceAtLeast(0L)
                _state.value = _state.value.copy(remainingMillis = remaining)
                notificationManager.notify(NOTIFICATION_ID, buildNotification(remaining))
            }
            if (isActive) {
                onCountdownFinished()
            }
        }
    }

    private fun onCountdownFinished() {
        _state.value = TimerUiState()
        notificationManager.notify(NOTIFICATION_ID, buildFinishedNotification())
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun stopCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _state.value = TimerUiState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        countdownJob?.cancel()
        super.onDestroy()
    }

    private fun buildNotification(remainingMillis: Long): Notification {
        val stopIntent = Intent(this, TimerService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(formatTime(remainingMillis))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.action_stop), stopPendingIntent)
            .build()
    }

    private fun buildFinishedNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(getString(R.string.timer_finished_title))
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun formatTime(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    companion object {
        const val ACTION_START = "com.example.timertest.action.START"
        const val ACTION_STOP = "com.example.timertest.action.STOP"
        const val EXTRA_DURATION_MILLIS = "com.example.timertest.extra.DURATION_MILLIS"

        private const val CHANNEL_ID = "timer_channel"
        private const val NOTIFICATION_ID = 1
        private const val TICK_INTERVAL_MILLIS = 1000L

        private val _state = MutableStateFlow(TimerUiState())
        val state = _state.asStateFlow()
    }
}

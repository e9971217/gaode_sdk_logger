package com.example.gaode_sdk_logger.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class TrackingService : Service() {

    private var tracker: LocationTracker? = null
    private var writer: TrackWriter? = null
    private var gnss: GnssRecorder? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            stopSelf()
            return START_NOT_STICKY
        }
        startTracking()
        return START_STICKY
    }

    override fun onDestroy() {
        stopTracking()
        super.onDestroy()
    }

    private fun startTracking() {
        // 透過 startForegroundService 啟動時，必須盡快呼叫 startForeground，即使已經在記錄
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (tracker != null) return

        LocationTracker.setPrivacyConsent(this)
        val newWriter = TrackWriter(this)
        writer = newWriter
        TrackingRepository.setTrackFile(newWriter.file.absolutePath)

        // 與軌跡檔使用相同的時間戳記，方便對照
        val gnssRecorder = GnssRecorder(this, newWriter.file.nameWithoutExtension.removePrefix("track_"))
        gnssRecorder.start()
        gnss = gnssRecorder
        TrackingRepository.setGnssFile(gnssRecorder.file.absolutePath)
        TrackingRepository.setRecording(true)
        tracker = LocationTracker(this) { loc ->
            TrackingRepository.onFix(loc)
            newWriter.write(loc)
        }.also { it.start() }
    }

    private fun stopTracking() {
        tracker?.destroy()
        tracker = null
        writer?.close()
        writer = null
        gnss?.stop()
        gnss = null
        TrackingRepository.setRecording(false)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "定位記錄", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("正在記錄軌跡")
            .setContentText("高德定位記錄中")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "停止", stopIntent)
            .build()
    }

    companion object {
        private const val ACTION_STOP = "com.example.gaode_sdk_logger.STOP_TRACKING"
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TrackingService::class.java).setAction(ACTION_STOP))
        }
    }
}

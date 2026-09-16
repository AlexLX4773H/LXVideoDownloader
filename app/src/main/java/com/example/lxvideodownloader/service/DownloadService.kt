package com.example.lxvideodownloader.service

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
import androidx.core.app.ServiceCompat
import com.example.lxvideodownloader.MainActivity
import com.example.lxvideodownloader.R
import com.example.lxvideodownloader.core.download.DownloadManager
import com.example.lxvideodownloader.core.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DownloadService : Service {

    constructor() : super()

    companion object {
        const val CHANNEL_ID = "lx_download_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.lxvideodownloader.action.START"
        const val ACTION_CANCEL = "com.example.lxvideodownloader.action.CANCEL"
        const val EXTRA_TASK_ID = "extra_task_id"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                if (!taskId.isNullOrBlank()) {
                    DownloadManager.cancelDownload(taskId)
                }
            }
            ACTION_START -> {
                startForegroundWithNotification()
                observeTasks()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val initialNotification = buildNotification("Starting download...", "", 0, 100, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                initialNotification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                }
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }
    }

    private fun observeTasks() {
        if (observeJob != null) return

        observeJob = serviceScope.launch {
            DownloadManager.tasks.collectLatest { tasks ->
                val activeTask = tasks.firstOrNull { it.status == DownloadStatus.DOWNLOADING }
                if (activeTask != null) {
                    val progressInt = (activeTask.progress * 100).toInt()
                    val speed = formatSpeed(activeTask.speedBytesPerSec)
                    val content = "${activeTask.downloadedSegments}/${activeTask.totalSegments} segments ($speed)"
                    val notification = buildNotification(
                        title = "Downloading: ${activeTask.title}",
                        content = content,
                        progress = progressInt,
                        max = 100,
                        taskId = activeTask.id
                    )
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, notification)
                } else {
                    // No active downloads left
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun buildNotification(
        title: String,
        content: String,
        progress: Int,
        max: Int,
        taskId: String?
    ): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setProgress(max, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)

        if (taskId != null) {
            val cancelIntent = Intent(this, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_TASK_ID, taskId)
            }
            val cancelPending = PendingIntent.getService(
                this,
                taskId.hashCode(),
                cancelIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPending)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Video Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of M3U8 video downloads"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024.0 * 1024))
            bytesPerSec >= 1024 -> String.format("%.0f KB/s", bytesPerSec / 1024.0)
            else -> "$bytesPerSec B/s"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        observeJob?.cancel()
    }
}

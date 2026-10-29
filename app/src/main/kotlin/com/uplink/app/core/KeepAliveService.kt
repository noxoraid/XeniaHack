package com.uplink.app

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class KeepAliveService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var taskLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, "UntilyFlow", NotificationManager.IMPORTANCE_LOW))
        }
        pin()
        scope.launch {
            combine(Hub.count, Hub.wake, TaskBoard.brief) { c, w, t -> Triple(c, w, t) }.collect { (c, w, t) ->
                holdForTasks(t.active > 0)
                getSystemService(NotificationManager::class.java).notify(ID, build(c, w, t))
            }
        }
    }

    private fun pin() {
        ServiceCompat.startForeground(
            this, ID, build(Hub.count.value, Hub.wake.value, TaskBoard.brief.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACT_EXIT -> exit()
            ACT_WAKE -> toggleWake()
            else -> pin()
        }
        return START_NOT_STICKY
    }

    private fun holdForTasks(on: Boolean) {
        if (on && taskLock == null) {
            taskLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "untilyflow:task")
                .apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }
        } else if (!on && taskLock != null) {
            taskLock?.release()
            taskLock = null
        }
    }

    private fun toggleWake() {
        if (Hub.wake.value) {
            wakeLock?.release()
            wakeLock = null
            Hub.wake.value = false
        } else {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "untilyflow:ssh")
                .apply { setReferenceCounted(false); acquire() }
            Hub.wake.value = true
        }
    }

    private fun exit() {
        wakeLock?.release()
        wakeLock = null
        taskLock?.release()
        taskLock = null
        Hub.wake.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Thread {
            ExitReport.expectExit(this)
            try { Hub.closeAll() } catch (_: Exception) {}
            android.os.Process.killProcess(android.os.Process.myPid())
        }.start()
    }

    private fun build(count: Int, wake: Boolean, tasks: TaskBrief): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val openIntent = Intent(this, MainActivity::class.java)
        if (tasks.active > 0) openIntent.putExtra(MainActivity.EXTRA_ROUTE, MainActivity.ROUTE_TASKS)
        val open = PendingIntent.getActivity(this, 0, openIntent, flags)
        val exit = PendingIntent.getService(
            this, 1, Intent(this, KeepAliveService::class.java).setAction(ACT_EXIT), flags,
        )
        val reshow = PendingIntent.getService(
            this, 3, Intent(this, KeepAliveService::class.java).setAction(ACT_RESHOW), flags,
        )
        val wakeIntent = PendingIntent.getActivity(
            this, 2, Intent(this, WakeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags,
        )
        val text = when {
            tasks.active == 1 -> tasks.label
            tasks.active > 1 -> tr("{0} tasks running", tasks.active)
            count == 0 -> tr("Ready")
            count == 1 -> tr("1 session")
            else -> tr("{0} sessions", count)
        }
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("UntilyFlow")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(NxAccent.toArgb())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .setDeleteIntent(reshow)
            .addAction(0, tr("Exit"), exit)
            .addAction(0, if (wake) tr("Release wakelock") else tr("Acquire wakelock"), wakeIntent)
        if (tasks.active > 0) {
            if (tasks.total > 0) builder.setProgress(tasks.total, tasks.done.coerceAtMost(tasks.total), false)
            else builder.setProgress(0, 0, true)
        }
        return builder.build()
            .apply { this.flags = this.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT }
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.release()
        taskLock?.release()
        super.onDestroy()
    }

    companion object {
        const val ID = 1
        const val CHANNEL = "untilyflow_main"
        const val ACT_EXIT = "com.uplink.app.EXIT"
        const val ACT_WAKE = "com.uplink.app.WAKE"
        const val ACT_RESHOW = "com.uplink.app.RESHOW"
    }
}

class WakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!Hub.wake.value && !pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        startService(Intent(this, KeepAliveService::class.java).setAction(KeepAliveService.ACT_WAKE))
        finish()
    }
}

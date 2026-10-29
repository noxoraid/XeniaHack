package com.uplink.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat

object TaskAlerts {
    private const val CHANNEL = "untilyflow_tasks"
    private const val RUNNING_BASE = 4000
    private const val ENDED_BASE = 900_000

    @Volatile private var app: Context? = null

    fun attach(ctx: Context) {
        val appCtx = ctx.applicationContext
        app = appCtx
        if (Build.VERSION.SDK_INT >= 26) {
            appCtx.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, tr("Tasks"), NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    fun running(run: TaskRun) {
        val ctx = app ?: return
        post(ctx, RUNNING_BASE + run.id, build(ctx, run, ongoing = true))
    }

    fun ended(run: TaskRun) {
        val ctx = app ?: return
        cancel(ctx, RUNNING_BASE + run.id)
        post(ctx, ENDED_BASE + run.id, build(ctx, run, ongoing = false))
    }

    private fun build(ctx: Context, run: TaskRun, ongoing: Boolean): Notification {
        val open = PendingIntent.getActivity(
            ctx,
            run.id,
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, MainActivity.ROUTE_TASKS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(run.title)
            .setContentText(summary(run, ongoing))
            .setContentIntent(open)
            .setWhen(run.startedAt)
            .setShowWhen(true)
            .setColor(NxAccent.toArgb())
            .setCategory(if (ongoing) NotificationCompat.CATEGORY_PROGRESS else NotificationCompat.CATEGORY_STATUS)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (ongoing) {
            val pct = (run.fraction * 100).toInt()
            if (pct >= 0) builder.setProgress(100, pct, false) else builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun summary(run: TaskRun, ongoing: Boolean): String {
        val middle = if (ongoing) run.detail.ifBlank { run.counter() } else run.error ?: run.state.label
        val tail = if (ongoing) run.speed() else run.elapsed()
        return listOf(run.kind.label, middle, tail).filter { it.isNotBlank() }.joinToString("  ")
    }

    private fun post(ctx: Context, id: Int, notification: Notification) {
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun cancel(ctx: Context, id: Int) {
        ctx.getSystemService(NotificationManager::class.java).cancel(id)
    }
}

package com.uplink.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat

class ApprovalAsk(val id: Int, val place: String, val command: String, val reason: String) {
    var verdict by mutableIntStateOf(0)
}

object ApprovalBoard {
    private const val CHANNEL = "untilyflow_approval"
    private const val BASE = 700_000
    private const val WAIT = 10 * 60 * 1000L
    const val ACT_ALLOW = "com.uplink.app.APPROVAL_ALLOW"
    const val ACT_DENY = "com.uplink.app.APPROVAL_DENY"
    const val EXTRA_ID = "approval_id"

    val pending = mutableStateListOf<ApprovalAsk>()

    @Volatile var visible = false

    private var seq = 0

    fun attach(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, tr("Approvals"), NotificationManager.IMPORTANCE_HIGH))
        }
    }

    fun decide(id: Int, yes: Boolean) {
        pending.firstOrNull { it.id == id }?.verdict = if (yes) 1 else 2
    }

    fun request(ctx: Context, place: String, command: String, reason: String, stopped: () -> Boolean): Boolean {
        val ask = ApprovalAsk(synchronized(this) { ++seq }, place, command, reason)
        pending.add(ask)
        if (!visible) post(ctx, ask)
        val end = System.currentTimeMillis() + WAIT
        try {
            while (ask.verdict == 0) {
                if (stopped()) throw TaskStopped()
                if (System.currentTimeMillis() > end) ask.verdict = 2
                Thread.sleep(200)
            }
        } finally {
            pending.remove(ask)
            ctx.getSystemService(NotificationManager::class.java).cancel(BASE + ask.id)
        }
        return ask.verdict == 1
    }

    private fun button(ctx: Context, ask: ApprovalAsk, action: String, code: Int): PendingIntent {
        val intent = Intent(ctx, ApprovalReceiver::class.java).setAction(action).putExtra(EXTRA_ID, ask.id)
        return PendingIntent.getBroadcast(ctx, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun post(ctx: Context, ask: ApprovalAsk) {
        val open = PendingIntent.getActivity(
            ctx, ask.id, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val note = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(tr("AI asks permission"))
            .setContentText(ask.command)
            .setStyle(NotificationCompat.BigTextStyle().bigText(ask.place + "\n" + ask.reason + "\n\n" + ask.command))
            .setContentIntent(open)
            .setColor(NxAccent.toArgb())
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setTimeoutAfter(WAIT)
            .addAction(0, tr("Allow"), button(ctx, ask, ACT_ALLOW, ask.id * 2))
            .addAction(0, tr("Deny"), button(ctx, ask, ACT_DENY, ask.id * 2 + 1))
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(BASE + ask.id, note)
        } catch (_: SecurityException) {
        }
    }
}

class ApprovalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(ApprovalBoard.EXTRA_ID, -1)
        ApprovalBoard.decide(id, intent.action == ApprovalBoard.ACT_ALLOW)
        context.getSystemService(NotificationManager::class.java).cancel(700_000 + id)
    }
}

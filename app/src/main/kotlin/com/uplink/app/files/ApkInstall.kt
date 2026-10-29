package com.uplink.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File

object ApkInstall {
    fun start(ctx: Context, fs: FsBackend, path: String) {
        if (!isLocal(fs)) {
            toast(ctx, tr("APK install works in Internal and NoxoraStorage"))
            return
        }
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            toast(ctx, tr("Allow installs from this app first"))
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val app = ctx.applicationContext
        val file = File(path)
        TaskBoard.start(TaskKind.Install, file.name) { run ->
            run.totalBytes = file.length()
            val installer = app.packageManager.packageInstaller
            val id = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
            val session = installer.openSession(id)
            var committed = false
            try {
                copyInto(session, file, run)
                val sender = PendingIntent.getBroadcast(
                    app,
                    id,
                    Intent(app, InstallReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                ).intentSender
                session.commit(sender)
                committed = true
            } finally {
                if (!committed) session.abandon()
                session.close()
            }
            run.detail = tr("Confirm install in the system dialog")
        }
    }

    private fun copyInto(session: PackageInstaller.Session, file: File, run: TaskRun) {
        session.openWrite(file.name, 0L, file.length()).use { out ->
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    if (run.stop) throw TaskStopped()
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    run.bytes += n.toLong()
                    TaskBoard.publish()
                }
            }
            if (Build.VERSION.SDK_INT >= 26) session.fsync(out)
        }
    }
}

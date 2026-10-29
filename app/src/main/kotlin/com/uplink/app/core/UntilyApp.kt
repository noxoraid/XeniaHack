package com.uplink.app

import android.app.Application

class UntilyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashTrail.install(this)
        MindGate.init(this)
        Thread({
            ErrorLog.load()
            ExitReport.scan(this)
            ErrorLog.alertIfCrashed()
        }, "error-scan").start()
    }
}

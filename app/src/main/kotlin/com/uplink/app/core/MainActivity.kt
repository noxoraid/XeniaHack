package com.uplink.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TaskAlerts.attach(this)
        ApprovalBoard.attach(this)
        Lang.load(this)
        WebHost.prime(this)
        FontPrefs.load(this)
        TermZoom.load(this)
        Thread { MediaStage.sweep(applicationContext) }.start()
        NxMode.load(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        pinNotification()
        routeFrom(intent)

        setContent {
            NxTheme {
                var ready by rememberSaveable { mutableStateOf(false) }
                Box(Modifier.fillMaxSize().background(NxBg).nxSafe()) {
                    AnimatedContent(
                        targetState = ready,
                        transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(300)) },
                        label = "intro",
                    ) { done ->
                        if (done) AppShell() else Splash { ready = true }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeFrom(intent)
    }

    private fun routeFrom(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_ROUTE) == ROUTE_TASKS) Nav.route = Route.Tasks
    }

    override fun onStart() {
        super.onStart()
        ApprovalBoard.visible = true
        pinNotification()
    }

    override fun onStop() {
        ApprovalBoard.visible = false
        super.onStop()
    }

    private fun pinNotification() {
        ContextCompat.startForegroundService(this, Intent(this, KeepAliveService::class.java))
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        const val ROUTE_TASKS = "tasks"
    }
}

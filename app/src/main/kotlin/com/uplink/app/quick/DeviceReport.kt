package com.uplink.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.view.WindowManager

object DeviceReport {
    fun rows(ctx: Context): List<Pair<String, String>> {
        val battery = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val heat = (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val refresh = ctx.getSystemService(WindowManager::class.java).defaultDisplay.refreshRate
        return listOf(
            (tr("Battery")) to (if (percent >= 0) "$percent%" else tr("Unknown")),
            (tr("Charging")) to (if (charging) tr("Yes") else tr("No")),
            (tr("Battery temperature")) to ("%.1f °C".format(heat)),
            (tr("Screen refresh rate")) to ("%.0f Hz".format(refresh)),
            (tr("Wi-Fi")) to wifiName(ctx),
            (tr("Location")) to location(ctx),
        )
    }

    private fun wifiName(ctx: Context): String {
        val ssid = runCatching {
            ctx.applicationContext.getSystemService(WifiManager::class.java).connectionInfo?.ssid
        }.getOrNull()?.trim('"')
        return if (ssid.isNullOrEmpty() || ssid == "<unknown ssid>") tr("Not connected") else ssid
    }

    private fun location(ctx: Context): String {
        val granted = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) return tr("Permission needed")
        val manager = ctx.getSystemService(LocationManager::class.java)
        val fix = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstNotNullOfOrNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
        return fix?.let { "%.5f, %.5f".format(it.latitude, it.longitude) } ?: tr("Not available")
    }
}

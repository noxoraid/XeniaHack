package com.uplink.app

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object GoogleFontStore {
    private const val MIN_BYTES = 4_000L

    fun dir(ctx: Context): File = File(ctx.filesDir, "fonts").apply { mkdirs() }

    fun ensure(ctx: Context, url: String): File? {
        val name = url.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(dir(ctx), name)
        if (target.isFile && target.length() > MIN_BYTES) return target
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = true
            try {
                if (c.responseCode !in 200..299) return null
                val part = File(target.path + ".part")
                c.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
                if (part.length() <= MIN_BYTES) {
                    part.delete()
                    return null
                }
                if (target.exists()) target.delete()
                if (!part.renameTo(target)) return null
                target
            } finally {
                c.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}

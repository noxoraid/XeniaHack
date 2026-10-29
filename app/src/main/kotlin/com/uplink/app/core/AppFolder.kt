package com.uplink.app

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.IOException

object AppFolder {
    private const val ROOT = "Uplink"
    private const val LEGACY = "Uplink"

    @Volatile
    private var spare: File? = null

    fun init(ctx: Context) {
        spare = ctx.getExternalFilesDir(null) ?: ctx.filesDir
    }

    private fun root(): File {
        val shared = File(Environment.getExternalStorageDirectory(), ROOT)
        val old = File(Environment.getExternalStorageDirectory(), LEGACY)
        if (old.isDirectory && !shared.exists()) old.renameTo(shared)
        if ((shared.isDirectory || shared.mkdirs()) && shared.canWrite()) return shared
        val inner = File(spare ?: shared, ROOT)
        val innerOld = File(spare ?: shared, LEGACY)
        if (innerOld.isDirectory && !inner.exists()) innerOld.renameTo(inner)
        return inner.also { it.mkdirs() }
    }

    fun dir(feature: String): File = File(root(), feature).also { it.mkdirs() }

    fun unique(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 2
        while (true) {
            candidate = File(dir, "$stem ($n)$ext")
            if (!candidate.exists()) return candidate
            n++
        }
    }

    fun writeAtomic(file: File, text: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            file.delete()
            if (!temp.renameTo(file)) throw IOException("Cannot write ${file.name}")
        }
    }
}

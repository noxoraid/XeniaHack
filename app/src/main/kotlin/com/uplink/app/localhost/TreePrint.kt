package com.uplink.app

import java.io.File

object TreePrint {
    private val loaded: Boolean = try {
        System.loadLibrary("nxcore")
        true
    } catch (_: Throwable) {
        false
    }

    private external fun nativeFingerprint(root: String, skipNames: Array<String>, skipPrefix: String): Long

    fun of(root: String, skipNames: List<String>, skipPrefix: String): Long =
        if (loaded) nativeFingerprint(root, skipNames.toTypedArray(), skipPrefix)
        else slow(File(root), skipNames, skipPrefix)

    private fun slow(root: File, skipNames: List<String>, skipPrefix: String): Long {
        var h = 1469598103934665603L
        fun mix(text: String) {
            for (ch in text) {
                h = h xor ch.code.toLong()
                h *= 1099511628211L
            }
        }
        root.walkTopDown().onEnter { it.name !in skipNames }.forEach { entry ->
            if (entry.name in skipNames || (skipPrefix.isNotEmpty() && entry.name.startsWith(skipPrefix))) return@forEach
            mix("${entry.relativeTo(root).path}|${entry.lastModified()}|${entry.length()}")
        }
        return h
    }
}

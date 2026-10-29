package com.uplink.app

object GitHash {
    private val loaded: Boolean = try {
        System.loadLibrary("nxcore")
        true
    } catch (_: Throwable) {
        false
    }

    private external fun nativeBlob(path: String): String?

    fun blob(path: String): String? {
        if (!loaded) return null
        return try {
            nativeBlob(path)
        } catch (_: Throwable) {
            null
        }
    }
}

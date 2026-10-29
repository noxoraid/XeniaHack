package com.uplink.app

enum class DeleteRefusal { FilesystemRoot, StorageRoot, MediaFolder }

object DeletePolicy {
    private val media = setOf(
        "download", "downloads", "dcim", "documents", "pictures", "movies", "music", "alarms",
        "audiobooks", "notifications", "podcasts", "recordings", "ringtones", "android",
    )

    fun segments(path: String): List<String> {
        val out = ArrayList<String>()
        for (part in path.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (out.isNotEmpty()) out.removeAt(out.size - 1)
                else -> out.add(part)
            }
        }
        return out
    }

    private fun isNumber(text: String): Boolean = text.isNotEmpty() && text.all { it in '0'..'9' }

    private fun storageForm(parts: List<String>): List<String> {
        if (parts.isEmpty()) return parts
        return when {
            parts[0] == "sdcard" -> listOf("storage", "emulated", "0") + parts.drop(1)
            parts.size >= 2 && parts[0] == "mnt" && parts[1] == "sdcard" -> listOf("storage", "emulated", "0") + parts.drop(2)
            parts.size >= 3 && parts[0] == "storage" && parts[1] == "self" && parts[2] == "primary" ->
                listOf("storage", "emulated", "0") + parts.drop(3)
            parts.size >= 4 && parts[0] == "mnt" && parts[1] == "user" && isNumber(parts[2]) && parts[3] == "primary" ->
                listOf("storage", "emulated", parts[2]) + parts.drop(4)
            parts.size >= 3 && parts[0] == "data" && parts[1] == "media" && isNumber(parts[2]) ->
                listOf("storage", "emulated", parts[2]) + parts.drop(3)
            else -> parts
        }
    }

    fun canonical(path: String): String = "/" + storageForm(segments(path)).joinToString("/")

    fun refusal(path: String): DeleteRefusal? {
        val parts = storageForm(segments(path))
        if (parts.isEmpty()) return DeleteRefusal.FilesystemRoot
        if (parts[0] != "storage") return null
        if (parts.size == 1) return DeleteRefusal.StorageRoot
        val volume = if (parts[1] == "emulated") {
            if (parts.size == 2) return DeleteRefusal.StorageRoot
            if (!isNumber(parts[2])) return null
            3
        } else {
            2
        }
        if (parts.size == volume) return DeleteRefusal.StorageRoot
        if (parts.size == volume + 1 && parts[volume].lowercase() in media) return DeleteRefusal.MediaFolder
        return null
    }

    fun confirmNames(paths: List<String>): List<String> = paths.map { canonical(it) }.distinct()

    fun needsConfirm(command: String, overwrites: Boolean): Boolean = when (command) {
        "rm", "rmdir" -> true
        "mv", "cp", "write", "unzip", "zip" -> overwrites
        else -> false
    }
}

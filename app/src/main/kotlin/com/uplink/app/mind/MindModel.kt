package com.uplink.app

class MindDecl(val kind: String, val name: String, val line: Int)

class MindFileEntry(
    val path: String,
    val size: Long,
    val lang: String,
    val modified: Long,
    val decls: List<MindDecl>,
    val imports: List<String>,
)

class MindHeader(
    val name: String,
    val root: String,
    val fileCount: Int,
    val indexedAt: Long,
    val signature: String,
    val truncated: Boolean,
)

class MindIndexDoc(val header: MindHeader, val files: List<MindFileEntry>) {
    val newestModified: Long get() = files.maxOfOrNull { it.modified } ?: 0L
}

class MindEntry(val name: String, val dir: Boolean, val size: Long, val modified: Long)

interface MindSource {
    fun list(dir: String): List<MindEntry>
    fun read(path: String, limit: Int): String
}

enum class MindLevel { Ok, Warning, Error }

class MindFinding(val level: MindLevel, val check: String, val message: String, val path: String = "")

enum class MindSurface(val key: String, val tools: Boolean) {
    Phone("phone", true),
    Ssh("ssh", true),
    Chat("chat", false),
    Flow("flow", false),
}

class MindCancelled : Exception("Stopped")

class MindStepError(val step: String, val path: String, cause: Throwable) :
    Exception("$step failed at $path: ${cause.message ?: cause.toString()}")

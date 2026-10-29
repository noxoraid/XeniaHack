package com.uplink.app

object MindDetect {
    const val MAX_UP = 8

    val markers = listOf(".git", "build.gradle", "build.gradle.kts", "package.json", "pyproject.toml", "Cargo.toml", "go.mod")

    val codeExtensions = setOf(
        "kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "mjs", "cjs", "c", "h", "cpp", "cc", "cxx", "hpp", "hh",
        "go", "rs", "rb", "php", "swift", "cs", "dart", "sh", "lua", "html", "css", "scss", "vue", "sql", "gradle",
    )

    fun isCode(name: String): Boolean = extensionOf(name) in codeExtensions

    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot < 0 || dot == name.length - 1) "" else name.substring(dot + 1).lowercase()
    }

    fun parentOf(path: String): String? {
        val clean = path.trimEnd('/')
        if (clean.isEmpty()) return null
        val cut = clean.lastIndexOf('/')
        return if (cut <= 0) "/" else clean.substring(0, cut)
    }

    fun root(folder: String, source: MindSource): String? {
        val start = folder.trimEnd('/').ifEmpty { "/" }
        if (broad(start)) return null
        val own = try { source.list(start) } catch (e: Exception) { throw MindStepError("detect", start, e) }
        if (markers.any { marker -> own.any { it.name == marker } }) return start
        var dir = if (start == "/") null else parentOf(start)
        var climbed = 1
        while (dir != null && climbed <= MAX_UP && !broad(dir)) {
            val names = try { source.list(dir).map { it.name } } catch (_: Exception) { emptyList() }
            if (markers.any { it in names }) return dir
            dir = if (dir == "/") null else parentOf(dir)
            climbed++
        }
        return if (own.any { !it.dir && isCode(it.name) }) start else null
    }

    private val broadFixed = setOf("/", "/storage", "/sdcard", "/data", "/data/data", "/mnt", "/storage/emulated", "/storage/self", "/storage/self/primary", "/system")
    private val broadPatterns = listOf(
        Regex("^/storage/emulated/\\d+$"),
        Regex("^/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}$"),
        Regex("^/mnt/user/\\d+/primary$"),
    )

    fun broad(path: String): Boolean {
        val p = path.trimEnd('/').ifEmpty { "/" }
        return p in broadFixed || broadPatterns.any { it.matches(p) }
    }

    fun nameOf(root: String): String = root.trimEnd('/').substringAfterLast('/').ifEmpty { "root" }

    fun hash(root: String): String {
        var h = -0x340d631b7bdddcdbL
        for (b in root.trimEnd('/').toByteArray(Charsets.UTF_8)) {
            h = h xor (b.toLong() and 0xff)
            h *= 0x100000001b3L
        }
        return java.lang.Long.toHexString(h).padStart(16, '0')
    }
}

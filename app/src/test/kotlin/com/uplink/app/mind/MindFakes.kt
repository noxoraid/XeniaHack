package com.uplink.app

class MindMemorySource(private val tree: Map<String, List<MindEntry>>, private val texts: Map<String, String> = emptyMap()) : MindSource {
    override fun list(dir: String): List<MindEntry> = tree[dir] ?: throw java.io.IOException("no such folder $dir")
    override fun read(path: String, limit: Int): String = (texts[path] ?: throw java.io.IOException("no such file $path")).take(limit)
}

fun mindDir(name: String) = MindEntry(name, true, 0, 0)

fun mindFile(name: String, size: Long = 10, modified: Long = 0) = MindEntry(name, false, size, modified)

fun mindDoc(vararg files: MindFileEntry, at: Long = 1000L, truncated: Boolean = false) =
    MindIndexDoc(MindHeader("demo", "/p/demo", files.size, at, "sig", truncated), files.toList())

fun mindEntry(path: String, decls: List<MindDecl> = emptyList(), imports: List<String> = emptyList(), size: Long = 100, modified: Long = 0, lang: String = "kotlin") =
    MindFileEntry(path, size, lang, modified, decls, imports)

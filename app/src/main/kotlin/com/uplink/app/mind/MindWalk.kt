package com.uplink.app

class MindFileRef(val path: String, val size: Long, val modified: Long, val lang: String)

class MindLimits(val files: Int, val dirs: Int, val depth: Int, val readChars: Int, val outlineBytes: Long) {
    companion object {
        val local = MindLimits(6000, 1500, 12, 262144, 1_000_000L)
        val remote = MindLimits(1500, 400, 10, 65536, 300_000L)
    }
}

class MindScanResult(val refs: List<MindFileRef>, val truncated: Boolean, val problems: List<String>) {
    val newestModified: Long get() = refs.maxOfOrNull { it.modified } ?: 0L
    val signature: String get() = MindWalk.signature(refs)
}

class MindBuildResult(val doc: MindIndexDoc, val problems: List<String>)

object MindWalk {
    fun join(dir: String, name: String): String = if (dir == "/") "/$name" else "${dir.trimEnd('/')}/$name"

    fun signature(refs: List<MindFileRef>): String {
        var h = -0x340d631b7bdddcdbL
        for (r in refs.sortedBy { it.path }) {
            for (b in (r.path + ":" + r.size + "\n").toByteArray(Charsets.UTF_8)) {
                h = h xor (b.toLong() and 0xff)
                h *= 0x100000001b3L
            }
        }
        return java.lang.Long.toHexString(h).padStart(16, '0')
    }

    fun scan(root: String, source: MindSource, limits: MindLimits, stopped: () -> Boolean, progress: (Int) -> Unit = {}): MindScanResult {
        val refs = ArrayList<MindFileRef>()
        val problems = ArrayList<String>()
        val pending = ArrayList<Pair<String, Int>>()
        pending.add(root to 0)
        var dirs = 0
        var truncated = false
        while (pending.isNotEmpty()) {
            if (stopped()) throw MindCancelled()
            val (dir, depth) = pending.removeAt(pending.size - 1)
            if (dirs >= limits.dirs) {
                truncated = true
                break
            }
            dirs++
            val children = try {
                source.list(dir)
            } catch (e: Exception) {
                if (dir == root) throw MindStepError("scan", dir, e)
                problems.add(MindStepError("scan", dir, e).message.orEmpty())
                continue
            }
            for (child in children.sortedBy { it.name }) {
                if (child.dir) {
                    if (child.name in MindLang.skippedDirs || child.name.startsWith(".")) continue
                    if (depth + 1 > limits.depth) {
                        truncated = true
                        continue
                    }
                    pending.add(join(dir, child.name) to depth + 1)
                    continue
                }
                val lang = MindLang.of(child.name) ?: continue
                if (refs.size >= limits.files) {
                    truncated = true
                    break
                }
                val full = join(dir, child.name)
                refs.add(MindFileRef(relative(root, full), child.size, child.modified, lang))
            }
            progress(refs.size)
        }
        return MindScanResult(refs.sortedBy { it.path }, truncated, problems)
    }

    fun relative(root: String, full: String): String {
        val base = root.trimEnd('/')
        return if (full.startsWith("$base/")) full.substring(base.length + 1) else full.trimStart('/')
    }

    fun build(
        root: String, source: MindSource, scan: MindScanResult, limits: MindLimits, now: Long,
        stopped: () -> Boolean, progress: (Int, Int, String) -> Unit = { _, _, _ -> },
    ): MindBuildResult {
        val files = ArrayList<MindFileEntry>()
        val problems = ArrayList(scan.problems)
        for ((index, ref) in scan.refs.withIndex()) {
            if (stopped()) throw MindCancelled()
            progress(index, scan.refs.size, ref.path)
            var decls: List<MindDecl> = emptyList()
            var imports: List<String> = emptyList()
            if (MindLang.outlined(ref.lang) && ref.size <= limits.outlineBytes) {
                try {
                    val text = source.read(join(root, ref.path), limits.readChars)
                    val outline = MindOutline.of(ref.lang, text)
                    decls = outline.decls
                    imports = outline.imports
                } catch (e: Exception) {
                    problems.add(MindStepError("read", join(root, ref.path), e).message.orEmpty())
                }
            }
            files.add(MindFileEntry(ref.path, ref.size, ref.lang, ref.modified, decls, imports))
        }
        progress(scan.refs.size, scan.refs.size, "")
        val header = MindHeader(MindDetect.nameOf(root), root, files.size, now, scan.signature, scan.truncated)
        return MindBuildResult(MindIndexDoc(header, files), problems)
    }
}

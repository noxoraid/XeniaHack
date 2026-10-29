package com.uplink.app

class NxZipUnsafe(message: String) : Exception(message)

class NxZipEntry(val name: String, val dir: Boolean, val size: Long)

class NxUnzipLimits(val maxEntries: Int, val maxBytes: Long) {
    companion object {
        val DEFAULT = NxUnzipLimits(50_000, 2L * 1024 * 1024 * 1024)
        const val MAX_REPLACED = 30
    }
}

class NxUnzipItem(val entryName: String, val rel: String, val dir: Boolean, val size: Long)

class NxUnzipPlan(val items: List<NxUnzipItem>, val totalBytes: Long, val replaces: List<String>)

object NxZipPath {
    fun clean(raw: String, say: NxSay): String {
        if (raw.indexOf('\u0000') >= 0) throw NxZipUnsafe(say("unsafe entry in zip: {0}", arrayOf(show(raw))))
        val name = raw.replace('\\', '/')
        if (name.startsWith("/") || (name.length >= 2 && name[1] == ':' && name[0].isLetter())) {
            throw NxZipUnsafe(say("unsafe entry in zip, absolute path: {0}", arrayOf(show(raw))))
        }
        val parts = ArrayList<String>()
        for (part in name.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> throw NxZipUnsafe(say("unsafe entry in zip, path leaves the target folder: {0}", arrayOf(show(raw))))
                else -> parts.add(part)
            }
        }
        return parts.joinToString("/")
    }

    private fun show(raw: String): String = raw.replace('\u0000', '?').take(120)
}

object NxUnzip {
    fun check(count: Int, bytes: Long, limits: NxUnzipLimits, say: NxSay) {
        if (count > limits.maxEntries) {
            throw NxZipUnsafe(say("zip has {0} entries, the limit is {1}", arrayOf(count, limits.maxEntries)))
        }
        if (bytes > limits.maxBytes) {
            throw NxZipUnsafe(say("zip would extract to {0}, the limit is {1}", arrayOf(NxFormat.size(bytes), NxFormat.size(limits.maxBytes))))
        }
    }

    fun plan(entries: List<NxZipEntry>, target: String, limits: NxUnzipLimits, kindOf: (String) -> Int, say: NxSay): NxUnzipPlan {
        var declared = 0L
        for (e in entries) declared += e.size.coerceAtLeast(0L)
        check(entries.size, declared, limits, say)
        val root = DeletePolicy.canonical(target).trimEnd('/')
        val items = ArrayList<NxUnzipItem>()
        val replaces = LinkedHashSet<String>()
        for (e in entries) {
            val rel = NxZipPath.clean(e.name, say)
            if (rel.isEmpty()) continue
            val full = NxFormat.join(target.ifEmpty { "/" }, rel)
            if (!DeletePolicy.canonical(full).startsWith("$root/")) {
                throw NxZipUnsafe(say("unsafe entry in zip, path leaves the target folder: {0}", arrayOf(e.name.take(120))))
            }
            val have = kindOf(full)
            when {
                e.dir && have == 1 -> throw NxZipUnsafe(say("{0} exists as a file, cannot create a folder there", arrayOf(full)))
                !e.dir && have == 2 -> throw NxZipUnsafe(say("{0} exists as a folder, cannot write a file there", arrayOf(full)))
                !e.dir && have == 1 -> replaces.add(full)
            }
            items.add(NxUnzipItem(e.name, rel, e.dir, e.size.coerceAtLeast(0L)))
        }
        if (replaces.size > NxUnzipLimits.MAX_REPLACED) {
            throw NxZipUnsafe(say("{0} existing files would be replaced, extract into an empty folder instead", arrayOf(replaces.size)))
        }
        return NxUnzipPlan(items, declared, replaces.toList())
    }

    fun entriesFromNames(listing: String): List<NxZipEntry> =
        listing.lines().filter { it.isNotEmpty() }.map { NxZipEntry(it, it.endsWith("/"), 0L) }

    fun parseTotals(text: String): Pair<Int, Long>? {
        val m = Regex("""(\d+)\s+files?,\s+(\d+)\s+bytes\s+uncompressed""").find(text) ?: return null
        val count = m.groupValues[1].toIntOrNull() ?: return null
        val bytes = m.groupValues[2].toLongOrNull() ?: return null
        return count to bytes
    }
}

class NxUnzipMeter(private val limits: NxUnzipLimits, private val say: NxSay) {
    var total = 0L
        private set

    fun add(n: Int) {
        total += n
        if (total > limits.maxBytes) {
            throw NxZipUnsafe(say("extracted data passed the limit of {0}, stopped", arrayOf(NxFormat.size(limits.maxBytes))))
        }
    }
}

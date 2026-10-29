package com.uplink.app

import java.io.File
import java.io.IOException

object MindStore {
    const val MAX_BYTES = 24_000_000L

    fun base(): File = AppFolder.dir("mind")

    fun folder(root: String): File = File(base(), MindDetect.hash(root)).also { it.mkdirs() }

    fun file(root: String): File = File(File(base(), MindDetect.hash(root)), "index.tsv")

    fun write(doc: MindIndexDoc): MindFinding {
        val target = File(folder(doc.header.root), "index.tsv")
        try {
            AppFolder.writeAtomic(target, MindIndexFile.write(doc))
        } catch (e: Exception) {
            throw MindStepError("write", target.path, e)
        }
        val back = try {
            target.readText()
        } catch (e: Exception) {
            throw MindStepError("reread", target.path, e)
        }
        return MindValidate.check(back)
    }

    fun read(root: String): String? {
        val target = file(root)
        if (!target.isFile) return null
        if (target.length() > MAX_BYTES) throw MindStepError("read", target.path, IOException("index is larger than $MAX_BYTES bytes"))
        return try {
            target.readText()
        } catch (e: Exception) {
            throw MindStepError("read", target.path, e)
        }
    }

    fun deleteAll(): Int {
        var removed = 0
        for (child in base().listFiles().orEmpty()) {
            if (!child.deleteRecursively()) throw MindStepError("delete", child.path, IOException("cannot delete"))
            removed++
        }
        return removed
    }
}

package com.uplink.app

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

class RunLogText(val full: String, val errors: String)

object RunLog {
    private const val ENTRY_CAP = 4 * 1024 * 1024
    private const val TOTAL_CAP = 8 * 1024 * 1024
    private val stamp = Regex("^\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z ")
    private val hit = Regex(
        "##\\[error\\]|\\berror\\b|\\bFAILED\\b|\\bfailure\\b|exception|what went wrong|fatal|\\be: |unresolved reference|cannot find symbol|not found",
        RegexOption.IGNORE_CASE,
    )
    private val detail = Regex("what went wrong|execution failed|\\be: ", RegexOption.IGNORE_CASE)

    fun fetch(repo: String, runId: Long): RunLogText {
        val parts = ArrayList<Pair<String, String>>()
        var total = 0
        ZipInputStream(BufferedInputStream(GitHubApi.fetch("/repos/$repo/actions/runs/$runId/logs"), 65536)).use { z ->
            while (total < TOTAL_CAP) {
                val e = z.nextEntry ?: break
                if (e.isDirectory || e.name.contains('/')) continue
                val out = ByteArrayOutputStream()
                val buf = ByteArray(65536)
                while (out.size() < ENTRY_CAP) {
                    val n = z.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
                total += out.size()
                parts.add(e.name to out.toString(Charsets.UTF_8.name()))
            }
        }
        if (parts.isEmpty()) throw Exception(tr("No log available for this run"))
        parts.sortBy { p -> p.first.substringBefore('_').toIntOrNull() ?: Int.MAX_VALUE }
        val full = StringBuilder()
        for ((name, text) in parts) {
            full.append("===== ").append(name.removeSuffix(".txt")).append(" =====\n")
            for (line in text.split('\n')) full.append(stamp.replaceFirst(line.trimEnd('\r'), "")).append('\n')
        }
        return RunLogText(full.toString(), errorsOf(full.toString()))
    }

    fun errorsOf(log: String): String {
        val lines = log.split('\n')
        val out = StringBuilder()
        var i = 0
        var last = -1
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("=====") || hit.containsMatchIn(line)) {
                if (line.startsWith("=====")) {
                    i++
                    continue
                }
                if (i != last + 1 && out.isNotEmpty()) out.append("...\n")
                out.append(line).append('\n')
                last = i
                if (detail.containsMatchIn(line)) {
                    var k = 1
                    while (k <= 4 && i + k < lines.size) {
                        out.append(lines[i + k]).append('\n')
                        last = i + k
                        k++
                    }
                    i += k - 1
                }
            }
            i++
        }
        return out.toString().trim().ifEmpty { tr("No errors found") }
    }

    fun save(repo: String, number: Int, errorsOnly: Boolean, text: String): File {
        val dir = File(AppFolder.dir("github"), "logs").also { it.mkdirs() }
        val name = repo.replace('/', '_') + "-run" + number + (if (errorsOnly) "-errors" else "-full") + ".txt"
        val file = AppFolder.unique(dir, name)
        file.writeText(text, Charsets.UTF_8)
        return file
    }
}

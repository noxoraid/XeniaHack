package com.uplink.app

class MindSummaryCheck(val text: String, val findings: List<MindFinding>)

class MindStaleCheck(val stale: Boolean, val reason: String)

object MindChecks {
    const val SIZE_RATIO = 1.5
    const val FAIL_LIMIT = 3
    const val BUILD_LIMIT = 2

    fun summary(summary: String, budget: Int, indexExists: Boolean): MindSummaryCheck {
        if (indexExists && summary.isBlank()) {
            return MindSummaryCheck(summary, listOf(MindFinding(MindLevel.Warning, "summary", "An index exists but the summary is empty")))
        }
        if (summary.length > budget) {
            val cut = MindSummary.fit(summary, budget)
            val message = "Summary is ${summary.length} characters, budget is $budget. Cut to ${cut.length} at a line boundary"
            return MindSummaryCheck(cut, listOf(MindFinding(MindLevel.Error, "summary", message)))
        }
        return MindSummaryCheck(summary, emptyList())
    }

    fun size(surface: MindSurface, sentOn: Int, meter: MindMeter): MindFinding? {
        val base = meter.averageOff(surface) ?: return null
        if (base <= 0.0) return null
        if (sentOn <= base * SIZE_RATIO) return null
        val ratio = sentOn / base
        val message = "${surface.key}: $sentOn characters sent with Mind on, ${"%.1f".format(ratio)} times the Mind off average of ${base.toLong()} (${meter.countOff(surface)} requests)"
        return MindFinding(MindLevel.Warning, "size", message)
    }

    fun stale(header: MindHeader, newestModified: Long, signature: String): MindStaleCheck {
        if (newestModified > header.indexedAt) {
            return MindStaleCheck(true, "a file changed at ${MindSummary.stamp(newestModified)}, index is from ${MindSummary.stamp(header.indexedAt)}")
        }
        if (header.signature.isNotEmpty() && signature.isNotEmpty() && header.signature != signature) {
            return MindStaleCheck(true, "the file list or file sizes changed since the index was built")
        }
        return MindStaleCheck(false, "")
    }
}

class MindPause {
    var paused: Boolean = false
        private set
    var reason: String = ""
        private set

    private var streak = 0
    private val failures = ArrayList<String>()
    private val builds = HashMap<String, Int>()

    @Synchronized
    fun request(mindOn: Boolean, ok: Boolean, why: String): Boolean {
        if (!mindOn || paused) return false
        if (ok) {
            streak = 0
            failures.clear()
            return false
        }
        streak++
        failures.add(why)
        if (streak < MindChecks.FAIL_LIMIT) return false
        paused = true
        reason = "Mind paused after ${MindChecks.FAIL_LIMIT} failed requests in a row: " + failures.takeLast(MindChecks.FAIL_LIMIT).joinToString("; ")
        return true
    }

    @Synchronized
    fun build(folder: String, ok: Boolean, why: String): Boolean {
        if (paused) return false
        if (ok) {
            builds.remove(folder)
            return false
        }
        val n = (builds[folder] ?: 0) + 1
        builds[folder] = n
        if (n < MindChecks.BUILD_LIMIT) return false
        paused = true
        reason = "Mind paused after $n failed index builds for $folder: $why"
        return true
    }

    @Synchronized
    fun resume() {
        paused = false
        reason = ""
        streak = 0
        failures.clear()
        builds.clear()
    }
}

package com.uplink.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MindChecksTests {
    private fun valid(): String = MindIndexFile.write(mindDoc(mindEntry("a/B.kt", listOf(MindDecl("class", "B", 1)), listOf("x.y")), mindEntry("c.py", lang = "python")))

    @Test
    fun validIndexPasses() {
        assertEquals(MindLevel.Ok, MindValidate.check(valid()).level)
    }

    @Test
    fun roundTripKeepsEverything() {
        val doc = MindIndexFile.parse(valid()).doc!!
        assertEquals(2, doc.files.size)
        assertEquals("class B:1", doc.files[0].decls[0].let { it.kind + " " + it.name + ":" + it.line })
        assertEquals(listOf("x.y"), doc.files[0].imports)
        assertEquals("/p/demo", doc.header.root)
    }

    @Test
    fun garbageFailsParseWithLine() {
        val f = MindValidate.check("hello\nworld")
        assertEquals(MindLevel.Error, f.level)
        assertEquals("parse", f.check)
        assertTrue(f.message.startsWith("line 1"))
        assertEquals("parse", MindValidate.check("").check)
    }

    @Test
    fun countMismatchIsReportedExactly() {
        val bad = valid().replace("files\t2", "files\t5")
        val f = MindValidate.check(bad)
        assertEquals("count", f.check)
        assertEquals("header says 5 files, index has 2 entries", f.message)
    }

    @Test
    fun emptyPathIsReportedWithPreviousEntry() {
        val bad = valid().replace("F\tc.py", "F\t")
        val f = MindValidate.check(bad)
        assertEquals("path", f.check)
        assertTrue(f.message.contains("entry 2"))
        assertEquals("a/B.kt", f.path)
    }

    @Test
    fun badNumberAndOrphanRowsFailParse() {
        assertEquals("parse", MindValidate.check(valid().replace("F\ta/B.kt\t100", "F\ta/B.kt\tabc")).check)
        assertEquals("parse", MindValidate.check("#mind-index\t1\nD\tfun\tx\t1\n").check)
    }

    @Test
    fun emptySummaryWithIndexWarns() {
        val c = MindChecks.summary("  ", 4000, true)
        assertEquals(MindLevel.Warning, c.findings.single().level)
        assertTrue(MindChecks.summary("", 4000, false).findings.isEmpty())
    }

    @Test
    fun overBudgetSummaryIsErrorAndCutAtLine() {
        val text = "line one\nline two\nline three"
        val c = MindChecks.summary(text, 12, true)
        assertEquals(MindLevel.Error, c.findings.single().level)
        assertEquals("line one", c.text)
        assertTrue(MindChecks.summary(text, 100, true).findings.isEmpty())
    }

    @Test
    fun sizeWarningNamesSurfaceAboveOnePointFive() {
        val m = MindMeter()
        for (i in 1..4) m.record(MindSurface.Chat, false, 1000)
        assertNull(MindChecks.size(MindSurface.Chat, 1500, m))
        val w = MindChecks.size(MindSurface.Chat, 1501, m)
        assertNotNull(w)
        assertEquals(MindLevel.Warning, w.level)
        assertTrue(w.message.startsWith("chat:"))
        assertNull(MindChecks.size(MindSurface.Ssh, 99999, m))
    }

    @Test
    fun pausesAfterThreeFailuresInARow() {
        val p = MindPause()
        assertFalse(p.request(true, false, "provider error: 500"))
        assertFalse(p.request(true, false, "empty reply"))
        assertTrue(p.request(true, false, "invalid JSON"))
        assertTrue(p.paused)
        assertTrue(p.reason.contains("provider error: 500") && p.reason.contains("invalid JSON"))
        p.resume()
        assertFalse(p.paused)
    }

    @Test
    fun successOrMindOffResetsNothingWrong() {
        val p = MindPause()
        p.request(true, false, "a")
        p.request(true, false, "b")
        p.request(true, true, "")
        assertFalse(p.request(true, false, "c"))
        assertFalse(p.request(false, false, "off request is ignored"))
        assertFalse(p.request(true, false, "d"))
        assertFalse(p.paused)
    }

    @Test
    fun pausesAfterTwoFailedBuildsOfSameFolder() {
        val p = MindPause()
        assertFalse(p.build("/a", false, "x"))
        assertFalse(p.build("/b", false, "y"))
        assertTrue(p.build("/a", false, "z"))
        assertTrue(p.reason.contains("/a") && p.reason.contains("z"))
    }

    @Test
    fun buildSuccessClearsFolderCount() {
        val p = MindPause()
        p.build("/a", false, "x")
        p.build("/a", true, "")
        assertFalse(p.build("/a", false, "x"))
    }

    @Test
    fun staleByModifiedTimeOrSignature() {
        val h = MindHeader("n", "/r", 1, 1000L, "abc", false)
        assertFalse(MindChecks.stale(h, 1000L, "abc").stale)
        assertTrue(MindChecks.stale(h, 1001L, "abc").stale)
        assertTrue(MindChecks.stale(h, 0L, "zzz").stale)
        assertFalse(MindChecks.stale(h, 0L, "").stale)
    }
}

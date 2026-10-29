package com.uplink.app

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class NxStageSevenTests {
    private val say = NxPlain
    private val ready = NxCatalog.readyNames().toSet()
    private val phoneKnown = setOf("pwd", "cd", "ls", "cat", "head", "tail", "wc", "stat", "du", "tree", "find", "grep", "play", "show", "mkdir", "touch", "cp", "mv", "rm", "rmdir", "zip", "unzip", "help", "date", "echo")

    private fun phone() = NxRetry(true, phoneKnown, ready)

    private fun vps() = NxRetry(false, phoneKnown, ready)

    private fun refusal(gate: NxGate): String = (gate as NxGate.Refuse).message

    @Test
    fun toyboxDuGetsOneRefusalAndNxDu() {
        val r = phone()
        val first = refusal(r.check("nx sh 'toybox du -sh /sdcard/x'", say))
        assertTrue(first.contains("nx du"), first)
        val again = listOf("nx sh busybox du /sdcard/x", "nx sh /system/bin/du /sdcard/x", "toybox du", "nx sh env du -s /sdcard/x")
        for (line in again) {
            val m = refusal(r.check(line, say))
            assertTrue(m.startsWith("Already refused"), "$line -> $m")
            assertTrue(m.contains("nx du"), m)
        }
    }

    @Test
    fun plainKnownCommandStillPassesOnPhone() {
        val r = phone()
        assertTrue(r.check("du /sdcard/x", say) is NxGate.Pass)
        assertTrue(r.check("nx sh du /sdcard/x", say) is NxGate.Pass)
        assertTrue(r.check("nx du /sdcard/x", say) is NxGate.Pass)
    }

    @Test
    fun unknownPlainNameOnPhoneIsRefusedOnceWithTwin() {
        val r = phone()
        assertTrue(refusal(r.check("sha256sum a.bin", say)).contains("nx hash"))
        assertTrue(refusal(r.check("sha256sum b.bin", say)).startsWith("Already refused"))
        assertTrue(refusal(r.check("curl https://x.test", say)).contains("nx fetch"))
    }

    @Test
    fun unknownNameWithoutTwinPointsToHelp() {
        assertTrue(refusal(phone().check("awk '{print}' f", say)).contains("nx help"))
    }

    @Test
    fun vpsRunsWrappedCommandsAndLearnsFromNotFound() {
        val r = vps()
        assertTrue(r.check("nx sh toybox du /x", say) is NxGate.Pass)
        r.record("nx sh pdftotext a.pdf", false, "sh: 1: pdftotext: not found")
        assertTrue(refusal(r.check("nx sh pdftotext b.pdf", say)).startsWith("Already refused"))
    }

    @Test
    fun twoFailedAttemptsAtOneGoalThenReport() {
        val r = vps()
        r.record("nx edit /a.txt 'x' 'y'", false, "old text appears 0 times")
        assertTrue(r.check("nx edit /a.txt 'x ' 'y'", say) is NxGate.Pass)
        r.record("nx edit /a.txt 'x ' 'y'", false, "old text appears 0 times")
        val m = refusal(r.check("nx edit /a.txt 'xx' 'y'", say))
        assertTrue(m.startsWith("Stopped after 2 attempts at nx edit /a.txt"), m)
        assertTrue(m.contains("nx edit /a.txt 'x' 'y'") && m.contains("nx edit /a.txt 'x ' 'y'"), m)
        assertTrue(r.check("nx edit /b.txt 'x' 'y'", say) is NxGate.Pass)
    }

    @Test
    fun successClearsTheGoal() {
        val r = vps()
        r.record("nx read /a", false, "x")
        r.record("nx read /a", true, "ok")
        r.record("nx read /a", false, "x")
        assertTrue(r.check("nx read /a", say) is NxGate.Pass)
    }

    @Test
    fun unavailableActionIsNotRetried() {
        val r = phone()
        r.record("nx node a.js", false, "nx node: needs a connected VPS. Run nx env to see what is available.")
        val m = refusal(r.check("nx node b.js", say))
        assertTrue(m.startsWith("Already refused: nx node"), m)
        assertTrue(m.contains("nx env"), m)
    }

    @Test
    fun twinsPointOnlyToReadyCommands() {
        for ((unix, nx) in NxRetry.twins) assertTrue(nx in ready, "$unix -> $nx")
    }

    @Test
    fun squeezeCollapsesBlanksAndRepeats() {
        val text = "a  \n\n\n\nb\nb\nb\r\nc\n\n"
        assertEquals("a\n\nb  (x3)\nc", NxCondense.squeeze(text))
    }

    @Test
    fun forModelKeepsShortTextUntouched() {
        val text = "a\n\n\nb"
        assertEquals(text, NxCondense.forModel(text, 100, say))
    }

    @Test
    fun forModelCutsTheMiddleAndKeepsEnds() {
        val text = (1..400).joinToString("\n") { "line number $it" }
        val out = NxCondense.forModel(text, 1000, say)
        assertTrue(out.length <= 1100, out.length.toString())
        assertTrue(out.startsWith("line number 1\n"))
        assertTrue(out.endsWith("line number 400"))
        assertTrue(out.contains("characters left out of the middle"))
    }

    @Test
    fun forModelPrefersSqueezeWhenThatFits() {
        val text = (1..500).joinToString("\n") { "same line" }
        assertEquals("same line  (x500)", NxCondense.forModel(text, 200, say))
    }

    @Test
    fun pipeTablesBecomeOneLinePerItem() {
        val table = "Here:\n| Name | Size |\n|------|------|\n| a.bin | 4.9 KB |\n| b | 10 B |\nDone"
        val out = NxNoPipes.clean(table)
        assertEquals("Here:\nName  Size\na.bin  4.9 KB\nb  10 B\nDone", out)
        assertFalse(out.contains("|"))
    }

    @Test
    fun textWithoutTablesIsLeftAlone() {
        val text = "use a || b and c | d"
        assertEquals(text, NxNoPipes.clean(text))
    }

    @Test
    fun lsAndDuShareTheSameShape() {
        val ls = NxLs.lines("/p", listOf(NxNode("big", false, 2048), NxNode("d", true, 0)), emptyMap(), true, 50, say)
        assertEquals("2.0 KB  big", ls[1])
        assertEquals("not counted yet  d/", ls[2])
        val du = NxDu.render("/p", listOf(DuRow("big", false, 2048, 1, true), DuRow("d", true, 0, -1, false)), null, say)
        assertEquals("2.0 KB  total  /p  (partial, 1 not counted yet)", du[0])
        assertEquals("2.0 KB  big", du[1])
        assertEquals("not counted yet  d/", du[2])
    }
}

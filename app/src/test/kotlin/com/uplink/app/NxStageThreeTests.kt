package com.uplink.app

import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class NxStageThreeTests {
    private fun stream(text: String) = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))

    private val tree = mapOf(
        "/r" to listOf(NxNode("a", true, 0), NxNode("b.txt", false, 100), NxNode("c.bin", false, 50)),
        "/r/a" to listOf(NxNode("x.txt", false, 1000), NxNode("sub", true, 0)),
        "/r/a/sub" to listOf(NxNode("y.txt", false, 24)),
    )

    private fun lister(dir: String): List<NxNode> = tree[dir] ?: throw java.io.IOException("no $dir")

    @Test
    fun sizeFormatting() {
        assertEquals("0 B", NxFormat.size(0))
        assertEquals("1023 B", NxFormat.size(1023))
        assertEquals("1.0 KB", NxFormat.size(1024))
        assertEquals("1.5 MB", NxFormat.size(1572864))
        assertEquals("2.00 GB", NxFormat.size(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun permissionsFormatting() {
        assertEquals("-rw-r--r-- (0644)", NxFormat.permissions(0b110100100, false))
        assertEquals("drwxr-xr-x (0755)", NxFormat.permissions(0b111101101, true))
    }

    @Test
    fun duMeasureEqualsSumOfFileSizes() {
        val r = NxDu.measure("/r", 12, Long.MAX_VALUE, { 0L }, { false }, ::lister)
        assertEquals(100L + 50 + 1000 + 24, r.bytes)
        assertEquals(4, r.files)
        assertTrue(r.complete)
    }

    @Test
    fun duMeasureStopsAtDepthAndDeadline() {
        assertTrue(!NxDu.measure("/r", 1, Long.MAX_VALUE, { 0L }, { false }, ::lister).complete)
        var clock = 0L
        val timed = NxDu.measure("/r", 12, 1L, { clock++ }, { false }, ::lister)
        assertTrue(!timed.complete)
    }

    @Test
    fun duMeasureCanBeCancelled() {
        assertFailsWith<NxCancelled> { NxDu.measure("/r", 12, Long.MAX_VALUE, { 0L }, { true }, ::lister) }
    }

    @Test
    fun duRenderTotalFirstLargestFirstAndUncountedMarked() {
        val rows = listOf(
            DuRow("small", true, 10, 1, true), DuRow("big", true, 5000, 3, true),
            DuRow("later", true, 0, 0, false), DuRow("f.txt", false, 700, 1, true),
        )
        val out = NxDu.render("/p", rows, null, NxPlain)
        assertEquals("5.6 KB  total  /p  (partial, 1 not counted yet)", out[0])
        assertEquals("4.9 KB  big/  3 files", out[1])
        assertEquals("700 B  f.txt", out[2])
        assertEquals("10 B  small/  1 files", out[3])
        assertEquals("not counted yet  later/", out[4])
    }

    @Test
    fun duRenderUsesGivenTotal() {
        val out = NxDu.render("/p", listOf(DuRow("a", true, 100, -1, true)), 4196L, NxPlain)
        assertTrue(out[0].startsWith("4.1 KB  total"))
        assertEquals("100 B  a/", out[1])
    }

    @Test
    fun duParseOutputFromDu() {
        val m = NxDu.parseDu("4096\t.\n100\t./a\n12 ./b c\nbad line\n")
        assertEquals(4096L, m["."])
        assertEquals(100L, m["./a"])
        assertEquals(12L, m["./b c"])
        assertEquals(3, m.size)
    }

    @Test
    fun treeDepthAndMarkers() {
        val out = NxTree.lines("/r", 2, 100, false, emptySet(), NxPlain, { false }, ::lister)
        assertEquals(listOf("/r", "├── a/", "│   ├── x.txt", "│   └── sub/", "├── b.txt", "└── c.bin"), out)
    }

    @Test
    fun treeCutsWithContinueMessage() {
        val out = NxTree.lines("/r", 3, 3, false, emptySet(), NxPlain, { false }, ::lister)
        assertEquals(4, out.size)
        assertTrue(out.last().contains("continue with: nx tree"))
    }

    @Test
    fun treeSkipsDirsAndHidden() {
        val data = mapOf("/q" to listOf(NxNode(".hid", false, 1), NxNode("node_modules", true, 0)), "/q/node_modules" to listOf(NxNode("z", false, 1)))
        val out = NxTree.lines("/q", 3, 50, false, setOf("node_modules"), NxPlain, { false }) { data[it] ?: emptyList() }
        assertEquals(listOf("/q", "└── node_modules/"), out)
    }

    @Test
    fun headTakesFirstLines() {
        assertEquals(listOf("a", "b"), NxLines.head(stream("a\nb\nc\nd\n"), 2) { false })
        assertEquals(listOf("a"), NxLines.head(stream("a"), 5) { false })
        assertEquals(emptyList(), NxLines.head(stream(""), 5) { false })
    }

    @Test
    fun tailTakesLastLinesAndReportsCompleteness() {
        val r = NxLines.tail(stream("1\n2\n3\n4\n5\n"), 2, 1000) { false }
        assertEquals(listOf("4", "5"), r.lines)
        assertTrue(r.complete)
        val cut = NxLines.tail(stream("1\n2\n3\n4\n5\n"), 2, 4) { false }
        assertTrue(!cut.complete)
    }

    @Test
    fun headLongLineIsClipped() {
        val line = "x".repeat(5000)
        assertEquals(NxLines.LINE_CAP + 1, NxLines.head(stream(line), 1) { false }[0].length)
    }

    private val doc = "{\"name\":\"app\",\"n\":3,\"list\":[10,{\"k\":\"v\"},[1,2]],\"deep\":{\"a\":{\"b\":true}},\"s\":\"x\\\"y,}\"}"

    @Test
    fun jsonExtractsValues() {
        fun get(p: String) = NxJsonPath.extract(stream(doc), NxJsonPath.split(p), 1000, 1 shl 20)
        assertEquals("\"app\"", get("name"))
        assertEquals("3", get("n"))
        assertEquals("10", get("list.0"))
        assertEquals("{\"k\":\"v\"}", get("list.1"))
        assertEquals("\"v\"", get("list.1.k"))
        assertEquals("[1,2]", get("list.2"))
        assertEquals("true", get("deep.a.b"))
        assertEquals("\"x\\\"y,}\"", get("s"))
    }

    @Test
    fun jsonMissingKeysReturnNull() {
        fun get(p: String) = NxJsonPath.extract(stream(doc), NxJsonPath.split(p), 1000, 1 shl 20)
        assertNull(get("nope"))
        assertNull(get("list.9"))
        assertNull(get("name.x"))
        assertNull(get("deep.a.zzz"))
    }

    @Test
    fun jsonWholeAndCapped() {
        val whole = NxJsonPath.extract(stream(doc), emptyList(), 1000, 1 shl 20)
        assertEquals(doc, whole)
        val small = NxJsonPath.extract(stream(doc), emptyList(), 10, 1 shl 20)
        assertEquals(doc.substring(0, 10) + "…", small)
    }

    @Test
    fun jsonInvalidAndTooLarge() {
        assertFailsWith<NxJsonError> { NxJsonPath.extract(stream("{\"a\" 1}"), listOf("a"), 100, 1000) }
        assertFailsWith<NxJsonError> { NxJsonPath.extract(stream("{\"a\":[1,2"), listOf("a"), 100, 1000) }
        val big = "{\"pad\":\"" + "x".repeat(500) + "\",\"k\":1}"
        assertFailsWith<NxJsonError> { NxJsonPath.extract(stream(big), listOf("k"), 100, 100) }
    }

    @Test
    fun jsonIgnoresUnrelatedLargeSiblingsWithoutCapturingThem() {
        val big = "{\"pad\":\"" + "x".repeat(200000) + "\",\"k\":42}"
        assertEquals("42", NxJsonPath.extract(stream(big), listOf("k"), 100, 1 shl 20))
    }

    @Test
    fun diffIdentical() {
        assertEquals(listOf("Files are identical"), NxDiff.unified(listOf("a"), listOf("a"), "x", "y", 2, 100, NxPlain))
    }

    @Test
    fun diffShowsChangeWithContext() {
        val a = listOf("1", "2", "3", "4", "5", "6", "7", "8")
        val b = listOf("1", "2", "3", "four", "5", "6", "7", "8")
        val out = NxDiff.unified(a, b, "a.txt", "b.txt", 1, 100, NxPlain)
        assertEquals(listOf("--- a.txt", "+++ b.txt", "@@ -3,3 +3,3 @@", " 3", "-4", "+four", " 5"), out)
    }

    @Test
    fun diffAddsAndRemoves() {
        val out = NxDiff.unified(listOf("a", "b"), listOf("a", "x", "b", "c"), "p", "q", 0, 100, NxPlain)
        assertTrue(out.contains("+x"))
        assertTrue(out.contains("+c"))
        assertTrue(out.none { it.startsWith("-") && !it.startsWith("---") })
    }

    @Test
    fun diffCutsAndRefusesHugeInputs() {
        val a = (1..400).map { "a$it" }
        val b = (1..400).map { "b$it" }
        val out = NxDiff.unified(a, b, "p", "q", 0, 50, NxPlain)
        assertEquals("[cut after 50 lines]", out.last())
        val ha = (1..3000).map { "a$it" }
        val hb = (1..3000).map { "b$it" }
        assertTrue(NxDiff.unified(ha, hb, "p", "q", 2, 100, NxPlain)[0].startsWith("Files differ from line 1"))
    }

    @Test
    fun findGroupsByFolderAndCuts() {
        val hits = listOf("src/a/x.kt", "src/a/y.kt", "src/b/z.kt", "top.kt")
        val out = NxGroup.find(hits, 100, NxPlain)
        assertEquals(listOf("src/a/  (2)", "  x.kt", "  y.kt", "src/b/  (1)", "  z.kt", "./  (1)", "  top.kt"), out)
        val cut = NxGroup.find(hits, 2, NxPlain)
        assertTrue(cut.last().startsWith("[cut at 2 results"))
        assertTrue(NxGroup.find(hits + "… more results not shown", 100, NxPlain).last().startsWith("[cut at"))
    }

    @Test
    fun grepGroupsByFileWithPerFileLimit() {
        val lines = listOf("a.kt:1:foo", "a.kt:5:foo bar", "a.kt:9:foo", "b.kt:2:foo")
        val out = NxGroup.grep(lines, 2, 50, NxPlain)
        assertEquals(listOf("a.kt  (3)", "  1:foo", "  5:foo bar", "  … 1 more in this file", "b.kt  (1)", "  2:foo"), out.dropLast(1))
        assertTrue(out.last().startsWith("[cut at 50 matches"))
    }

    @Test
    fun grepGroupLimitStopsEarly() {
        val lines = (1..20).map { "f$it.kt:1:hit" }
        val out = NxGroup.grep(lines, 5, 3, NxPlain)
        assertEquals(3, out.count { it.startsWith("  ") })
        assertTrue(out.last().startsWith("[cut at 3 matches"))
    }

    @Test
    fun lsShowsCountsAndOrders() {
        val items = listOf(NxNode("zdir", true, 0), NxNode("a.txt", false, 10), NxNode("big.bin", false, 5000), NxNode("adir", true, 0))
        val counts = mapOf("adir" to 3, "zdir" to 0)
        val bySize = NxLs.lines("/p", items, counts, true, 100, NxPlain)
        assertEquals(listOf("/p  4.9 KB in files  (2 folders, 2 files)", "4.9 KB  big.bin", "10 B  a.txt", "not counted yet  adir/  3 items", "not counted yet  zdir/  0 items"), bySize)
        val byName = NxLs.lines("/p", items, counts, false, 100, NxPlain)
        assertEquals(listOf("not counted yet  adir/  3 items", "not counted yet  zdir/  0 items", "10 B  a.txt", "4.9 KB  big.bin"), byName.drop(1))
        assertTrue(bySize.none { it.contains("|") })
    }

    @Test
    fun lsLimitAndEmpty() {
        val many = (1..10).map { NxNode("f$it", false, 1) }
        val out = NxLs.lines("/p", many, emptyMap(), false, 4, NxPlain)
        assertTrue(out.last().startsWith("[6 more not shown"))
        assertTrue(NxLs.lines("/p", emptyList(), emptyMap(), false, 4, NxPlain).contains("(empty)"))
    }
}

package com.uplink.app

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class NxStageFourTests {
    private val say = NxPlain

    private fun editFails(text: String, old: String): String =
        assertFailsWith<NxChangeError> { NxEdit.apply(text, old, "x", "f.txt", say) }.message.orEmpty()

    @Test
    fun editRejectsMissingOldText() {
        val msg = editFails("alpha beta", "gamma")
        assertTrue(msg.contains("0 occurrences"), msg)
        assertTrue(msg.contains("f.txt"), msg)
    }

    @Test
    fun editRejectsMoreThanOneOccurrenceAndNamesTheCount() {
        val msg = editFails("a b a b a", "a")
        assertTrue(msg.contains("3 times"), msg)
    }

    @Test
    fun editCountsOverlappingMatches() {
        assertEquals(2, NxEdit.count("aaa", "aa"))
        assertTrue(editFails("aaa", "aa").contains("2 times"))
    }

    @Test
    fun editRejectsEmptyOldText() {
        assertTrue(editFails("abc", "").contains("empty"))
    }

    @Test
    fun editReplacesExactlyOnceAndReportsTheLine() {
        val r = NxEdit.apply("one\ntwo\nTARGET here\nfour\n", "TARGET", "DONE", "f", say)
        assertEquals("one\ntwo\nDONE here\nfour\n", r.text)
        assertEquals(3, r.line)
    }

    @Test
    fun editLeavesTheRestOfTheFileUntouched() {
        val text = "keep\r\nthis\r\nexact\r\n"
        val r = NxEdit.apply(text, "this", "that", "f", say)
        assertEquals("keep\r\nthat\r\nexact\r\n", r.text)
    }

    @Test
    fun editHintsAtCrlfWhenNewlineDoesNotMatch() {
        val msg = editFails("a\r\nb\r\n", "a\nb")
        assertTrue(msg.contains("CRLF"), msg)
    }

    @Test
    fun editCanDeleteWithEmptyReplacement() {
        val r = NxEdit.apply("a-b-c", "-b", "", "f", say)
        assertEquals("a-c", r.text)
    }

    @Test
    fun escapesDecodeNewlineTabAndBackslash() {
        assertEquals("a\nb\tc\\d", NxEscape.decode("a\\nb\\tc\\\\d"))
        assertEquals("keep \\x as is", NxEscape.decode("keep \\x as is"))
        assertEquals("trailing\\", NxEscape.decode("trailing\\"))
    }

    @Test
    fun writeParseReadsPathAndInlineText() {
        val a = NxWriteParse.parse("src/a.txt 'hello\\nworld'", 100, say)
        assertEquals("src/a.txt", a.path)
        assertEquals("hello\nworld", a.content)
        val b = NxWriteParse.parse("a.txt hi there", 100, say)
        assertEquals("hi there", b.content)
        val c = NxWriteParse.parse("'my file.txt' \"it's fine\"", 100, say)
        assertEquals("my file.txt", c.path)
        assertEquals("it's fine", c.content)
    }

    @Test
    fun writeParseAcceptsAnExplicitEmptyFile() {
        val a = NxWriteParse.parse("empty.txt ''", 100, say)
        assertEquals("", a.content)
    }

    @Test
    fun writeParseRejectsMissingTextAndTooLongText() {
        assertFailsWith<NxChangeError> { NxWriteParse.parse("only-a-path", 100, say) }
        assertFailsWith<NxChangeError> { NxWriteParse.parse("", 100, say) }
        val msg = assertFailsWith<NxChangeError> { NxWriteParse.parse("a.txt " + "x".repeat(50), 10, say) }.message.orEmpty()
        assertTrue(msg.contains("50") && msg.contains("10"), msg)
    }

    private val kinds = mapOf("/d" to 2, "/d/x.txt" to 1, "/d/sub" to 2, "/f.txt" to 1, "/g.txt" to 1, "/dir" to 2, "/dir/f.txt" to 1, "/dir/d" to 2)

    private fun resolve(src: String, dst: String): NxMove {
        val k = { p: String -> kinds[p] ?: 0 }
        return NxTransfer.resolve(src, k(src), dst, k(dst), k, say)
    }

    @Test
    fun transferIntoExistingFolderUsesTheSourceName() {
        val go = resolve("/f.txt", "/dir") as NxMove.Go
        assertEquals("/dir/f.txt", go.to)
        assertTrue(go.replaces)
    }

    @Test
    fun transferIntoFolderWithoutClashDoesNotReplace() {
        val go = resolve("/g.txt", "/dir") as NxMove.Go
        assertEquals("/dir/g.txt", go.to)
        assertEquals(false, go.replaces)
    }

    @Test
    fun transferOntoExistingFileIsAReplaceThatNeedsConfirmation() {
        val go = resolve("/f.txt", "/g.txt") as NxMove.Go
        assertTrue(go.replaces)
        assertTrue(DeletePolicy.needsConfirm("cp", go.replaces))
        assertTrue(DeletePolicy.needsConfirm("mv", go.replaces))
    }

    @Test
    fun transferToNewNameDoesNotNeedConfirmation() {
        val go = resolve("/f.txt", "/new.txt") as NxMove.Go
        assertEquals(false, go.replaces)
        assertEquals(false, DeletePolicy.needsConfirm("cp", go.replaces))
        assertEquals(false, DeletePolicy.needsConfirm("mv", go.replaces))
    }

    @Test
    fun transferRefusesMissingSourceSameTargetAndSelfNesting() {
        assertTrue(resolve("/nope", "/x") is NxMove.Refuse)
        assertTrue(resolve("/f.txt", "/f.txt") is NxMove.Refuse)
        assertTrue(resolve("/d", "/d/sub") is NxMove.Refuse)
        assertTrue(resolve("/d", "/d") is NxMove.Refuse)
    }

    @Test
    fun transferNeverReplacesAFolderOrLetsAFolderReplaceAFile() {
        assertTrue(resolve("/d", "/dir") is NxMove.Refuse)
        assertTrue(resolve("/d", "/f.txt") is NxMove.Refuse)
    }

    @Test
    fun transferSeesThroughStorageAliases() {
        val k = { p: String -> if (p == "/storage/emulated/0/a") 2 else 0 }
        val r = NxTransfer.resolve("/storage/emulated/0/a", 2, "/sdcard/a/b", 0, k, say)
        assertTrue(r is NxMove.Refuse)
    }

    @Test
    fun overwriteRuleCoversEveryChangingCommand() {
        for (c in listOf("mv", "cp", "write", "unzip", "zip")) {
            assertTrue(DeletePolicy.needsConfirm(c, true), c)
            assertEquals(false, DeletePolicy.needsConfirm(c, false), c)
        }
        assertTrue(DeletePolicy.needsConfirm("rm", false))
        assertTrue(DeletePolicy.needsConfirm("rmdir", false))
        assertEquals(false, DeletePolicy.needsConfirm("mkdir", true))
        assertEquals(false, DeletePolicy.needsConfirm("ls", true))
    }

    @Test
    fun confirmationNamesOneFile() {
        val t = listOf(NxTarget("/storage/emulated/0/Work/a.txt", false))
        assertEquals("Delete 1 item", NxConfirm.head(false, NxConfirm.distinct(t).size, say))
        assertEquals(listOf("- /storage/emulated/0/Work/a.txt"), NxConfirm.lines(t))
    }

    @Test
    fun confirmationNamesAllThreeFiles() {
        val t = listOf(
            NxTarget("/storage/emulated/0/Work/a.txt", false),
            NxTarget("/storage/emulated/0/Work/b.txt", false),
            NxTarget("/storage/emulated/0/Work/c.txt", false),
        )
        assertEquals("Delete 3 items", NxConfirm.head(false, NxConfirm.distinct(t).size, say))
        val lines = NxConfirm.lines(t)
        assertEquals(3, lines.size)
        assertTrue(lines[0].endsWith("a.txt") && lines[1].endsWith("b.txt") && lines[2].endsWith("c.txt"))
    }

    @Test
    fun confirmationCollapsesAliasesAndMarksFolders() {
        val t = listOf(
            NxTarget("/sdcard/Work/a.txt", false),
            NxTarget("/storage/emulated/0/Work/a.txt", false),
            NxTarget("/sdcard/Work/old", true),
        )
        assertEquals(listOf("- /storage/emulated/0/Work/a.txt", "- /storage/emulated/0/Work/old/"), NxConfirm.lines(t))
        assertEquals("Replace 1 item", NxConfirm.head(true, 1, say))
        assertEquals("Replace 2 items", NxConfirm.head(true, 2, say))
    }

    @Test
    fun storageRootAndTopLevelMediaFoldersAreRefusedWithoutAChoice() {
        assertEquals(DeleteRefusal.FilesystemRoot, DeletePolicy.refusal("/"))
        assertEquals(DeleteRefusal.StorageRoot, DeletePolicy.refusal("/sdcard"))
        assertEquals(DeleteRefusal.StorageRoot, DeletePolicy.refusal("/storage/emulated/0"))
        assertEquals(DeleteRefusal.StorageRoot, DeletePolicy.refusal("/sdcard/Download/.."))
        assertEquals(DeleteRefusal.MediaFolder, DeletePolicy.refusal("/storage/emulated/0/Download"))
        assertEquals(DeleteRefusal.MediaFolder, DeletePolicy.refusal("/sdcard/DCIM/"))
        assertEquals(DeleteRefusal.MediaFolder, DeletePolicy.refusal("/sdcard/Work/../Pictures"))
        assertNull(DeletePolicy.refusal("/sdcard/Download/photo.jpg"))
        assertNull(DeletePolicy.refusal("/sdcard/Work"))
    }

    @Test
    fun zipSlipEntriesAreRejected() {
        for (bad in listOf("../evil.txt", "a/../../evil.txt", "/etc/passwd", "..\\evil.txt", "C:\\win\\x.txt", "a/b/../../../x", "ok/..")) {
            assertFailsWith<NxZipUnsafe>(bad) { NxZipPath.clean(bad, say) }
        }
        assertFailsWith<NxZipUnsafe> { NxZipPath.clean("a\u0000b", say) }
    }

    @Test
    fun safeEntryNamesAreNormalised() {
        assertEquals("a/b/c.txt", NxZipPath.clean("a/b/c.txt", say))
        assertEquals("a/b", NxZipPath.clean("./a//b/", say))
        assertEquals("", NxZipPath.clean("./", say))
        assertEquals("dir/file.txt", NxZipPath.clean("dir\\file.txt", say))
    }

    private val limits = NxUnzipLimits(5, 1000)

    private fun entries(vararg names: String) = names.map { NxZipEntry(it, it.endsWith("/"), 10) }

    @Test
    fun planRejectsTheWholeArchiveWhenOneEntryEscapes() {
        val e = assertFailsWith<NxZipUnsafe> {
            NxUnzip.plan(entries("ok.txt", "../evil.txt"), "/t", limits, { 0 }, say)
        }
        assertTrue(e.message.orEmpty().contains("../evil.txt"), e.message)
    }

    @Test
    fun planRejectsTooManyEntries() {
        val e = assertFailsWith<NxZipUnsafe> {
            NxUnzip.plan(entries("a", "b", "c", "d", "e", "f"), "/t", limits, { 0 }, say)
        }
        assertTrue(e.message.orEmpty().contains("6") && e.message.orEmpty().contains("5"), e.message)
    }

    @Test
    fun planRejectsDeclaredSizeOverTheLimit() {
        val big = listOf(NxZipEntry("a.bin", false, 600), NxZipEntry("b.bin", false, 600))
        val e = assertFailsWith<NxZipUnsafe> { NxUnzip.plan(big, "/t", limits, { 0 }, say) }
        assertTrue(e.message.orEmpty().contains("limit"), e.message)
    }

    @Test
    fun planAcceptsAnArchiveAtTheLimits() {
        val ok = (1..5).map { NxZipEntry("f$it.txt", false, 200) }
        val plan = NxUnzip.plan(ok, "/t", limits, { 0 }, say)
        assertEquals(5, plan.items.size)
        assertEquals(1000L, plan.totalBytes)
        assertTrue(plan.replaces.isEmpty())
    }

    @Test
    fun planListsFilesThatWouldBeReplaced() {
        val kind = { p: String -> if (p == "/t/a.txt") 1 else 0 }
        val plan = NxUnzip.plan(entries("a.txt", "b.txt"), "/t", limits, kind, say)
        assertEquals(listOf("/t/a.txt"), plan.replaces)
    }

    @Test
    fun planRefusesFileOverFolderAndFolderOverFile() {
        assertFailsWith<NxZipUnsafe> { NxUnzip.plan(entries("a.txt"), "/t", limits, { if (it == "/t/a.txt") 2 else 0 }, say) }
        assertFailsWith<NxZipUnsafe> { NxUnzip.plan(entries("d/"), "/t", limits, { if (it == "/t/d") 1 else 0 }, say) }
    }

    @Test
    fun planRefusesToReplaceMoreFilesThanItCanName() {
        val many = NxUnzipLimits(100, 1_000_000)
        val names = (1..31).map { "f$it.txt" }.toTypedArray()
        val e = assertFailsWith<NxZipUnsafe> { NxUnzip.plan(entries(*names), "/t", many, { 1 }, say) }
        assertTrue(e.message.orEmpty().contains("31"), e.message)
    }

    @Test
    fun planSkipsEmptyNames() {
        val plan = NxUnzip.plan(entries("./", "x.txt"), "/t", limits, { 0 }, say)
        assertEquals(listOf("x.txt"), plan.items.map { it.rel })
    }

    @Test
    fun meterStopsWhenRealBytesPassTheLimit() {
        val meter = NxUnzipMeter(NxUnzipLimits(10, 100), say)
        meter.add(60)
        meter.add(40)
        assertEquals(100L, meter.total)
        assertFailsWith<NxZipUnsafe> { meter.add(1) }
    }

    @Test
    fun serverListingIsParsedAndChecked() {
        val names = NxUnzip.entriesFromNames("a.txt\nsub/\nsub/b.txt\n")
        assertEquals(listOf(false, true, false), names.map { it.dir })
        assertEquals(3 to 1234L, NxUnzip.parseTotals("3 files, 1234 bytes uncompressed, 456 bytes compressed:  63.0%"))
        assertEquals(1 to 5L, NxUnzip.parseTotals("1 file, 5 bytes uncompressed, 9 bytes compressed:  -80.0%"))
        assertNull(NxUnzip.parseTotals("cannot find zipfile directory"))
        assertFailsWith<NxZipUnsafe> { NxUnzip.check(3, 5_000_000_000L, NxUnzipLimits.DEFAULT, say) }
    }

    private fun tempZip(build: (ZipOutputStream) -> Unit): File {
        val f = File.createTempFile("nxzip", ".zip")
        f.deleteOnExit()
        ZipOutputStream(f.outputStream()).use(build)
        return f
    }

    private fun read(f: File): List<NxZipEntry> =
        ZipFile(f).use { z -> z.entries().asSequence().map { NxZipEntry(it.name, it.isDirectory, it.size) }.toList() }

    @Test
    fun aRealZipWithADotDotEntryIsRejected() {
        val f = tempZip { z ->
            z.putNextEntry(ZipEntry("good/readme.txt")); z.write("hi".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("../escaped.txt")); z.write("owned".toByteArray()); z.closeEntry()
        }
        val e = assertFailsWith<NxZipUnsafe> { NxUnzip.plan(read(f), "/storage/emulated/0/out", NxUnzipLimits.DEFAULT, { 0 }, say) }
        assertTrue(e.message.orEmpty().contains("escaped.txt"), e.message)
    }

    @Test
    fun aRealZipThatExpandsPastTheLimitIsRejectedFromItsHeaders() {
        val f = tempZip { z ->
            z.putNextEntry(ZipEntry("zeros.bin")); z.write(ByteArray(3_000_000)); z.closeEntry()
        }
        assertTrue(f.length() < 100_000)
        val e = assertFailsWith<NxZipUnsafe> { NxUnzip.plan(read(f), "/t", NxUnzipLimits(10, 1_000_000), { 0 }, say) }
        assertTrue(e.message.orEmpty().contains("limit"), e.message)
    }

    @Test
    fun aRealCleanZipProducesAPlan() {
        val f = tempZip { z ->
            z.putNextEntry(ZipEntry("app/")); z.closeEntry()
            z.putNextEntry(ZipEntry("app/main.kt")); z.write("fun main() {}".toByteArray()); z.closeEntry()
        }
        val plan = NxUnzip.plan(read(f), "/t", NxUnzipLimits.DEFAULT, { 0 }, say)
        assertEquals(listOf("app", "app/main.kt"), plan.items.map { it.rel })
        assertEquals(listOf(true, false), plan.items.map { it.dir })
    }

    @Test
    fun everyStageFourCommandIsReadyAndAvailableAnywhere() {
        val names = listOf("write", "edit", "mkdir", "cp", "mv", "rm", "zip", "unzip")
        for (n in names) {
            val entry = NxCatalog.all.first { it.name == n }
            assertTrue(entry.ready, n)
            assertTrue(NxPick.forCommand(n, EnvFacts(VpsState.NotConfigured, false)) is NxChoice.Use, n)
        }
        assertEquals(names.toSet(), NxCatalog.all.map { it.name }.toSet().intersect(names.toSet()))
    }

    @Test
    fun stageFourToolNamesDoNotClashWithOtherToolSets() {
        val changing = setOf("write", "edit", "mkdir", "cp", "mv", "rm", "zip", "unzip")
        val reading = setOf("ls", "du", "tree", "stat", "head", "tail", "hash", "diff", "json")
        assertTrue(changing.intersect(reading).isEmpty())
    }
}

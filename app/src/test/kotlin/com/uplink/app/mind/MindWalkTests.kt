package com.uplink.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MindWalkTests {
    private val tree = mapOf(
        "/p" to listOf(mindDir("src"), mindDir("node_modules"), mindDir(".git"), mindFile("README.md"), mindFile("logo.png")),
        "/p/src" to listOf(mindFile("App.kt", 50, 900), mindFile("util.py", 20, 800)),
        "/p/node_modules" to listOf(mindFile("x.js")),
    )
    private val texts = mapOf("/p/src/App.kt" to "class App\nfun go() {}\n", "/p/src/util.py" to "def f(): pass\n")

    @Test
    fun scanSkipsHiddenBuildAndBinaryFiles() {
        val r = MindWalk.scan("/p", MindMemorySource(tree, texts), MindLimits.local, { false })
        assertEquals(listOf("README.md", "src/App.kt", "src/util.py"), r.refs.map { it.path })
        assertEquals(900L, r.newestModified)
    }

    @Test
    fun buildProducesOutlineWithoutBodies() {
        val src = MindMemorySource(tree, texts)
        val scan = MindWalk.scan("/p", src, MindLimits.local, { false })
        val built = MindWalk.build("/p", src, scan, MindLimits.local, 5000L, { false })
        assertEquals(3, built.doc.header.fileCount)
        assertEquals("p", built.doc.header.name)
        assertEquals(listOf("App", "go"), built.doc.files.first { it.path == "src/App.kt" }.decls.map { it.name })
        val text = MindIndexFile.write(built.doc)
        assertTrue(!text.contains("pass") && !text.contains("{}"))
        assertEquals(MindLevel.Ok, MindValidate.check(text).level)
    }

    @Test
    fun unreadableFileBecomesProblemNotSilence() {
        val src = MindMemorySource(tree, mapOf("/p/src/util.py" to "x = 1\n"))
        val scan = MindWalk.scan("/p", src, MindLimits.local, { false })
        val built = MindWalk.build("/p", src, scan, MindLimits.local, 1L, { false })
        assertTrue(built.problems.single().startsWith("read failed at /p/src/App.kt: "))
    }

    @Test
    fun unreadableRootThrowsStepAndPath() {
        val e = assertFailsWith<MindStepError> { MindWalk.scan("/nope", MindMemorySource(emptyMap()), MindLimits.local, { false }) }
        assertEquals("scan", e.step)
        assertEquals("/nope", e.path)
        assertTrue(e.message!!.startsWith("scan failed at /nope: "))
    }

    @Test
    fun stopFlagCancels() {
        assertFailsWith<MindCancelled> { MindWalk.scan("/p", MindMemorySource(tree), MindLimits.local, { true }) }
    }

    @Test
    fun fileLimitMarksTruncated() {
        val many = mapOf("/p" to (1..30).map { mindFile("f$it.kt") })
        val r = MindWalk.scan("/p", MindMemorySource(many), MindLimits(10, 10, 5, 1000, 1000), { false })
        assertEquals(10, r.refs.size)
        assertTrue(r.truncated)
    }

    @Test
    fun signatureChangesWithSize() {
        val a = listOf(MindFileRef("a.kt", 1, 0, "kotlin"))
        val b = listOf(MindFileRef("a.kt", 2, 0, "kotlin"))
        assertTrue(MindWalk.signature(a) != MindWalk.signature(b))
        assertEquals(MindWalk.signature(a), MindWalk.signature(a))
    }
}

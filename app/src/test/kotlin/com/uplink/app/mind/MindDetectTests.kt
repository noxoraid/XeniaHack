package com.uplink.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MindDetectTests {
    @Test
    fun markerInParentIsFound() {
        val src = MindMemorySource(mapOf("/p/app/src" to listOf(mindFile("a.kt")), "/p/app" to listOf(mindDir("src")), "/p" to listOf(mindDir("app"), mindFile("build.gradle.kts")), "/" to listOf(mindDir("p"))))
        assertEquals("/p", MindDetect.root("/p/app/src", src))
    }

    @Test
    fun everyMarkerCounts() {
        for (m in MindDetect.markers) {
            val src = MindMemorySource(mapOf("/w" to listOf(mindFile(m))))
            assertEquals("/w", MindDetect.root("/w", src), m)
        }
    }

    @Test
    fun walkStopsAfterEightParents() {
        val tree = HashMap<String, List<MindEntry>>()
        var path = ""
        val chain = ArrayList<String>()
        for (i in 0..10) {
            path += "/d$i"
            chain.add(path)
        }
        for (p in chain) tree[p] = listOf(mindDir("x"))
        tree["/"] = listOf(mindFile(".git"))
        tree["/d0"] = listOf(mindFile(".git"))
        val deep = chain[9]
        assertNull(MindDetect.root(deep, MindMemorySource(tree)))
        val ok = chain[8]
        assertEquals("/d0", MindDetect.root(ok, MindMemorySource(tree)))
    }

    @Test
    fun fallbackUsesFolderWithCodeFile() {
        val src = MindMemorySource(mapOf("/w/x" to listOf(mindFile("main.py")), "/w" to listOf(mindDir("x")), "/" to listOf(mindDir("w"))))
        assertEquals("/w/x", MindDetect.root("/w/x", src))
    }

    @Test
    fun noMarkerAndNoCodeGivesNull() {
        val src = MindMemorySource(mapOf("/w" to listOf(mindFile("notes.png")), "/" to listOf(mindDir("w"))))
        assertNull(MindDetect.root("/w", src))
    }

    @Test
    fun unreadableStartFolderReportsStepAndPath() {
        val e = assertFailsWith<MindStepError> { MindDetect.root("/missing", MindMemorySource(emptyMap())) }
        assertEquals("detect", e.step)
        assertEquals("/missing", e.path)
    }

    @Test
    fun hashIsStableAndDiffers() {
        assertEquals(MindDetect.hash("/a/b"), MindDetect.hash("/a/b/"))
        assertNotEquals(MindDetect.hash("/a/b"), MindDetect.hash("/a/c"))
        assertEquals(16, MindDetect.hash("/a").length)
    }

    @Test
    fun storageRootIsNeverAProject() {
        val src = MindMemorySource(mapOf("/storage/emulated/0" to listOf(mindFile("package.json"), mindFile("a.js")), "/storage/emulated" to listOf(mindDir("0"))))
        assertNull(MindDetect.root("/storage/emulated/0", src))
        assertNull(MindDetect.root("/sdcard", src))
        assertTrue(MindDetect.broad("/storage/1A2B-3C4D"))
        assertTrue(!MindDetect.broad("/storage/emulated/0/zzzz"))
    }

    @Test
    fun walkDoesNotClimbIntoStorageRoot() {
        val src = MindMemorySource(mapOf(
            "/storage/emulated/0/zzzz" to listOf(mindFile("note.txt")),
            "/storage/emulated/0" to listOf(mindFile("package.json")),
        ))
        assertNull(MindDetect.root("/storage/emulated/0/zzzz", src))
    }
}

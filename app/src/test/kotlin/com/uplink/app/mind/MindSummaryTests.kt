package com.uplink.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MindSummaryTests {
    private val note = MindSummary.readLine(true, true)

    private fun bigDoc(): MindIndexDoc {
        val files = (1..400).map { mindEntry("src/pkg$it/File$it.kt", listOf(MindDecl("class", "Thing$it", 3), MindDecl("fun", "run$it", 9)), listOf("a.b.c$it")) }
        return MindIndexDoc(MindHeader("big", "/p/big", files.size, 5000L, "s", false), files)
    }

    @Test
    fun neverExceedsBudgetAndAlwaysEndsWithNote() {
        val doc = bigDoc()
        for (budget in listOf(300, 500, 777, 1000, 4000, 12000)) {
            val s = MindSummary.build(doc, "fix Thing7 login", budget, note)
            assertTrue(s.length <= budget, "budget $budget got ${s.length}")
            assertTrue(s.endsWith(note), "budget $budget")
            assertTrue(s.startsWith("Project: big"))
        }
    }

    @Test
    fun tinyBudgetStillWithinLimit() {
        for (budget in 1..200) assertTrue(MindSummary.build(bigDoc(), "x", budget, note).length <= budget)
    }

    @Test
    fun fitCutsAtLineBoundary() {
        val text = "aaaa\nbbbb\ncccc"
        assertEquals("aaaa\nbbbb", MindSummary.fit(text, 12))
        assertEquals("aaaa", MindSummary.fit(text, 8))
        assertEquals("aaaa\nbbbb\ncccc", MindSummary.fit(text, 14))
        assertEquals("aa", MindSummary.fit("aaaaaa", 2))
    }

    @Test
    fun matchesComeBeforeOtherFiles() {
        val s = MindSummary.build(bigDoc(), "change Thing250", 4000, note)
        val match = s.indexOf("Matches for this request:")
        val rest = s.indexOf("\nFiles:\n")
        assertTrue(match > 0 && rest > match)
        assertTrue(s.indexOf("src/pkg250/File250.kt") in (match + 1) until rest)
        assertTrue(s.contains("class Thing250:3"))
    }

    @Test
    fun scoreRanksNameThenDeclaration() {
        val byName = mindEntry("src/auth/Login.kt")
        val byDecl = mindEntry("src/other/Misc.kt", listOf(MindDecl("fun", "loginUser", 4)))
        val none = mindEntry("src/other/Zed.kt")
        val ranked = MindSummary.order(mindDoc(none, byDecl, byName), "fix the login screen").map { it.file.path }
        assertEquals(listOf("src/auth/Login.kt", "src/other/Misc.kt", "src/other/Zed.kt"), ranked)
    }

    @Test
    fun termsSplitCamelCaseAndDropStopWords() {
        assertEquals(listOf("login", "view"), MindSummary.terms("the loginView and the"))
        assertEquals(listOf("shop", "cart"), MindSummary.terms("buat ShopCart"))
    }

    @Test
    fun headerAlwaysFirstAndNoMatchesStillListsFiles() {
        val s = MindSummary.build(bigDoc(), "zzzzzz", 2000, note)
        val lines = s.split('\n')
        assertEquals("Project: big", lines[0])
        assertTrue(lines[1].startsWith("Root: "))
        assertTrue(s.contains("\nFiles:\n"))
        assertTrue(!s.contains("Matches for this request:"))
        assertTrue(s.contains("more files not listed"))
    }

    @Test
    fun noteVariantsDependOnSurface() {
        assertTrue(MindSummary.readLine(true, true).contains("nx read <path> <from> <to>"))
        assertTrue(!MindSummary.readLine(false, false).contains("nx read"))
    }

    @Test
    fun budgetIsClamped() {
        assertEquals(MindSummary.MIN_BUDGET, MindSummary.clampBudget(1))
        assertEquals(MindSummary.MAX_BUDGET, MindSummary.clampBudget(10_000_000))
        assertEquals(4000, MindSummary.clampBudget(4000))
    }
}

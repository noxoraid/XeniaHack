package com.uplink.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MindOutlineTests {
    private fun names(r: MindOutlineResult) = r.decls.map { it.kind + " " + it.name + ":" + it.line }

    @Test
    fun kotlinTopLevelOnly() {
        val text = """
package a.b
import kotlin.math.max
import a.c.Thing

data class User(val id: Int)
private const val LIMIT = 3
@Composable
fun Screen(x: Int) {
    val inner = 1
    fun nested() {}
}
fun String.shout(): String = this
object Store {
    fun inside() {}
}
interface Port
enum class Mode { A, B }
""".trimIndent()
        val r = MindOutline.of("kotlin", text)
        assertEquals(listOf("class User:5", "val LIMIT:6", "fun Screen:8", "fun shout:12", "object Store:13", "interface Port:16", "class Mode:17"), names(r))
        assertEquals(listOf("kotlin.math.max", "a.c.Thing"), r.imports)
    }

    @Test
    fun kotlinIgnoresCommentsAndStrings() {
        val text = "// fun fake() {}\n/* class Hidden {\n fun x() }\n*/\nval s = \"fun nope() {\"\nfun real() {}\n"
        assertEquals(listOf("val s:5", "fun real:6"), names(MindOutline.of("kotlin", text)))
    }

    @Test
    fun javaClassAndMethods() {
        val text = """
import java.util.List;
public class Shop {
    private int n = compute(1);
    public static void main(String[] args) {
        run();
    }
    Map<String, List<Integer>> build(int x) { return null; }
}
interface Pay {}
""".trimIndent()
        val r = MindOutline.of("java", text)
        assertEquals(listOf("class Shop:2", "fun main:4", "fun build:7", "interface Pay:9"), names(r))
        assertEquals(listOf("java.util.List"), r.imports)
    }

    @Test
    fun pythonDefsClassesAndImports() {
        val text = "import os, sys as s\nfrom pkg.mod import thing\nMAX = 3\n\"\"\"\ndef hidden():\n\"\"\"\n@deco\nasync def go(a):\n    x = 1\nclass Box:\n    def m(self): pass\n"
        val r = MindOutline.of("python", text)
        assertEquals(listOf("val MAX:3", "fun go:8", "class Box:10"), names(r))
        assertEquals(listOf("os", "sys", "pkg.mod"), r.imports)
    }

    @Test
    fun javascriptAndTypescript() {
        val text = "import a from 'react'\nimport './x.css'\nconst fs = require('fs')\nexport default class App {\n  render() {}\n}\nexport async function load() {\n  const inner = 1\n}\nconst arrow = () => 1\ninterface Props { a: number }\nexport type Id = string\n"
        val r = MindOutline.of("typescript", text)
        assertEquals(listOf("val fs:3", "class App:4", "fun load:7", "val arrow:10", "interface Props:11", "type Id:12"), names(r))
        assertEquals(listOf("react", "./x.css", "fs"), r.imports)
    }

    @Test
    fun cAndCppIncludingNamespace() {
        val text = "#include <stdio.h>\n#include \"util.h\"\n#define MAX 4\nstruct Node {\n  int v;\n};\nint main(int argc, char** argv) {\n  return 0;\n}\nnamespace app {\nclass Svc : public Base {\n};\nvoid helper(int x);\n}\nextern \"C\" {\nint exported(void);\n}\nstruct Fwd;\n"
        val r = MindOutline.of("cpp", text)
        assertEquals(listOf("define MAX:3", "struct Node:4", "fun main:7", "namespace app:10", "class Svc:11", "fun helper:13", "fun exported:16"), names(r))
        assertEquals(listOf("stdio.h", "util.h"), r.imports)
    }

    @Test
    fun unknownLanguageGivesNothing() {
        val r = MindOutline.of("markdown", "# title\nfun x()")
        assertTrue(r.decls.isEmpty() && r.imports.isEmpty())
    }
}

package com.uplink.app

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class NxStageSixTests {
    private val say = NxPlain

    private fun planRun(runtime: NxRuntime, vararg words: String): NxRunPlan = NxRun.plan(runtime, words.toList(), say)

    private fun planInstall(vararg words: String): NxInstallPlan = NxInstall.plan(words.toList(), say)

    private fun installRefused(vararg words: String): String =
        assertFailsWith<NxRunError>(words.joinToString(" ")) { NxInstall.plan(words.toList(), say) }.message.orEmpty()

    @Test
    fun runNeedsAFileWithTheRightExtension() {
        assertFailsWith<NxRunError> { planRun(NxRuntime.Node) }
        assertFailsWith<NxRunError> { planRun(NxRuntime.Node, "app.py") }
        assertFailsWith<NxRunError> { planRun(NxRuntime.Python, "app.js") }
        assertFailsWith<NxRunError> { planRun(NxRuntime.Node, "noextension") }
        assertEquals("a/b.mjs", planRun(NxRuntime.Node, "a/b.mjs").file)
        assertEquals("job.PY", planRun(NxRuntime.Python, "job.PY").file)
    }

    @Test
    fun runRefusesFlagsAndInlineCode() {
        assertFailsWith<NxRunError> { planRun(NxRuntime.Node, "-e", "process.exit()") }
        assertFailsWith<NxRunError> { planRun(NxRuntime.Node, "--eval=1.js") }
        assertFailsWith<NxRunError> { planRun(NxRuntime.Python, "-c", "import os") }
    }

    @Test
    fun runKeepsArgumentsAfterTheFileAndLimitsThem() {
        val plan = planRun(NxRuntime.Python, "main.py", "--mode", "fast", "a b")
        assertEquals(listOf("--mode", "fast", "a b"), plan.args)
        assertFailsWith<NxRunError> { NxRun.plan(NxRuntime.Node, listOf("x.js") + List(21) { "a" }, say) }
        assertEquals(20, NxRun.plan(NxRuntime.Node, listOf("x.js") + List(20) { "a" }, say).args.size)
        assertFailsWith<NxRunError> { planRun(NxRuntime.Node, "x.js", "a".repeat(501)) }
        assertFailsWith<NxRunError> { planRun(NxRuntime.Node, "x.js", "a\nb") }
        assertFailsWith<NxRunError> { planRun(NxRuntime.Node, "x.js", "a\u0000b") }
    }

    @Test
    fun runCommandQuotesEverything() {
        val cmd = NxRun.command(NxRuntime.Node, "/srv/it's here/app.js", listOf("a; rm -rf /", "\$(id)"), "/srv/it's here")
        assertTrue(cmd.contains("node '/srv/it'\\''s here/app.js' 'a; rm -rf /' '\$(id)'"))
        assertTrue(cmd.startsWith("cd '/srv/it'\\''s here' 2>/dev/null"))
        assertTrue(cmd.contains("timeout ${NxRun.LIMIT_SECONDS}"))
        assertTrue(cmd.trimEnd().endsWith("</dev/null"))
    }

    @Test
    fun runCommandUsesThePythonBinary() {
        val cmd = NxRun.command(NxRuntime.Python, "/a/b.py", emptyList(), "/a")
        assertTrue(cmd.contains("python3 '/a/b.py'"))
    }

    @Test
    fun exitCodeMarkerIsReadAndHidden() {
        val raw = "hello\nworld\n\n${NxRun.CODE_MARK}3\n"
        assertEquals(3, NxRun.codeOf(raw))
        assertEquals("hello\nworld", NxRun.visible(raw))
        assertEquals(1, NxRun.codeOf("no marker"))
        assertEquals("partial", NxRun.visible("partial"))
        assertTrue(NxRun.withCode("echo hi").endsWith("echo ${NxRun.CODE_MARK}\$__nx_code"))
    }

    @Test
    fun reportShowsOutputExitCodeAndTimeout() {
        assertEquals("ok", NxRun.report("ok", 0, say))
        assertEquals("(no output)", NxRun.report("", 0, say))
        assertEquals("boom\nExit code 2", NxRun.report("boom", 2, say))
        assertEquals("Exit code 2", NxRun.report("", 2, say))
        assertTrue(NxRun.report("late", 124, say).endsWith("Stopped after 10 minutes"))
    }

    @Test
    fun longOutputKeepsTheEndAndSaysHowMuchWasCut() {
        val text = (1..2000).joinToString("\n") { "line $it" }
        val out = NxRun.tail(text, 4000, say)
        assertTrue(out.startsWith("[earlier output cut, "))
        assertTrue(out.endsWith("line 2000"))
        assertTrue(out.length < 4200)
        val cut = Regex("cut, (\\d+) characters").find(out)!!.groupValues[1].toInt()
        val kept = out.substringAfter('\n')
        assertEquals(text.length, cut + kept.length)
        assertEquals("short", NxRun.tail("short", 4000, say))
    }

    @Test
    fun installDefaultsToAptAndTakesAManagerWord() {
        val apt = planInstall("nginx", "curl")
        assertEquals(NxManager.Apt, apt.manager)
        assertEquals(listOf("nginx", "curl"), apt.packages)
        assertEquals(NxManager.Npm, planInstall("npm", "express").manager)
        assertEquals(NxManager.Pip, planInstall("PIP", "requests").manager)
        assertEquals(listOf("requests"), planInstall("pip", "requests", "requests").packages)
    }

    @Test
    fun installNeedsAtLeastOnePackageAndAtMostTen() {
        installRefused()
        installRefused("npm")
        assertEquals(10, NxInstall.plan(List(10) { "pkg$it" }, say).packages.size)
        installRefused(*Array(11) { "pkg$it" })
    }

    @Test
    fun installRefusesFlagsPathsUrlsAndShellText() {
        for (bad in listOf("-y", "--force", "../evil", "/etc/passwd", "./local", "http://x.io/p.tgz", "git+https://x.io/r.git", "a;b", "a b", "a&&b", "\$(id)", "`id`", "a|b", "a>b", "a\nb", "")) {
            assertFalse(NxInstall.allowed(NxManager.Apt, bad), "apt $bad")
            assertFalse(NxInstall.allowed(NxManager.Npm, bad), "npm $bad")
            assertFalse(NxInstall.allowed(NxManager.Pip, bad), "pip $bad")
        }
        installRefused("npm", "-g")
        installRefused("pip", "--index-url=http://evil")
        installRefused("-y", "nginx")
    }

    @Test
    fun installAcceptsRealPackageNames() {
        assertTrue(NxInstall.allowed(NxManager.Apt, "libssl-dev"))
        assertTrue(NxInstall.allowed(NxManager.Apt, "g++"))
        assertTrue(NxInstall.allowed(NxManager.Apt, "python3.12"))
        assertTrue(NxInstall.allowed(NxManager.Apt, "nginx=1.24.0-2ubuntu7"))
        assertTrue(NxInstall.allowed(NxManager.Apt, "pkg=1:2.0+dfsg-1"))
        assertFalse(NxInstall.allowed(NxManager.Apt, "Nginx"))
        assertTrue(NxInstall.allowed(NxManager.Npm, "express"))
        assertTrue(NxInstall.allowed(NxManager.Npm, "express@4.19.2"))
        assertTrue(NxInstall.allowed(NxManager.Npm, "@types/node"))
        assertTrue(NxInstall.allowed(NxManager.Npm, "@types/node@latest"))
        assertTrue(NxInstall.allowed(NxManager.Npm, "left-pad"))
        assertFalse(NxInstall.allowed(NxManager.Npm, "@scope"))
        assertFalse(NxInstall.allowed(NxManager.Npm, "@/name"))
        assertFalse(NxInstall.allowed(NxManager.Npm, "a/b"))
        assertTrue(NxInstall.allowed(NxManager.Pip, "requests"))
        assertTrue(NxInstall.allowed(NxManager.Pip, "requests==2.32.3"))
        assertTrue(NxInstall.allowed(NxManager.Pip, "Django"))
        assertTrue(NxInstall.allowed(NxManager.Pip, "zope.interface"))
        assertFalse(NxInstall.allowed(NxManager.Pip, "requests>=2"))
        assertFalse(NxInstall.allowed(NxManager.Pip, "requests[security]"))
    }

    @Test
    fun installRefusesOverlongNames() {
        assertFalse(NxInstall.allowed(NxManager.Apt, "a".repeat(101)))
        assertFalse(NxInstall.allowed(NxManager.Npm, "a".repeat(215)))
        assertTrue(NxInstall.allowed(NxManager.Npm, "a".repeat(100)))
    }

    @Test
    fun installCommandsAreNonInteractiveAndQuoted() {
        val apt = NxInstall.command(planInstall("nginx", "curl"), "/root")
        assertTrue(apt.contains("DEBIAN_FRONTEND=noninteractive apt-get update -qq"))
        assertTrue(apt.contains("apt-get install -y 'nginx' 'curl' </dev/null"))
        assertTrue(apt.contains("sudo -n true"))
        assertTrue(apt.contains(NxInstall.NO_ROOT))
        val npm = NxInstall.command(planInstall("npm", "express"), "/srv/app one")
        assertTrue(npm.contains("cd '/srv/app one' && npm install --no-fund --no-audit 'express' </dev/null"))
        assertFalse(npm.contains(" -g"))
        val pip = NxInstall.command(planInstall("pip", "requests"), "/root")
        assertTrue(pip.contains("python3 -m pip install --no-input 'requests' </dev/null"))
    }

    @Test
    fun installDisplayNamesTheManagerAndFolder() {
        assertEquals("apt-get update; apt-get install -y nginx curl", NxInstall.display(planInstall("nginx", "curl"), "/root"))
        assertEquals("npm install express  (in /srv/app)", NxInstall.display(planInstall("npm", "express"), "/srv/app"))
        assertEquals("python3 -m pip install requests", NxInstall.display(planInstall("pip", "requests"), "/root"))
    }

    @Test
    fun installReportExplainsMissingToolsAndRoot() {
        val apt = planInstall("nginx")
        assertEquals(
            "Installing system packages needs root or passwordless sudo on this server",
            NxInstall.report(apt, "${NxInstall.NO_ROOT}\n", 126, say),
        )
        assertEquals(
            "npm is not installed on this server, nx install npm is not available here",
            NxInstall.report(planInstall("npm", "x"), "${NxInstall.MISSING}npm\n", 127, say),
        )
        assertTrue(NxInstall.report(planInstall("pip", "x"), "${NxInstall.MISSING}pip", 127, say).startsWith("pip is not installed"))
    }

    @Test
    fun installReportSuccessAndFailure() {
        val ok = NxInstall.report(planInstall("nginx"), "Setting up nginx\nDone.\n", 0, say)
        assertEquals("Installed nginx with apt\nDone.", ok)
        assertEquals("Installed nginx with apt", NxInstall.report(planInstall("nginx"), "", 0, say))
        val failed = NxInstall.report(planInstall("nope"), "E: Unable to locate package nope", 100, say)
        assertTrue(failed.startsWith("Install failed (exit code 100)"))
        assertTrue(failed.contains("Unable to locate package nope"))
    }

    @Test
    fun pipReportAddsTheVirtualEnvironmentHint() {
        val out = NxInstall.report(planInstall("pip", "requests"), "error: externally-managed-environment\n", 1, say)
        assertTrue(out.contains("python3 -m venv"))
        val other = NxInstall.report(planInstall("pip", "requests"), "error: no matching distribution\n", 1, say)
        assertFalse(other.contains("venv"))
    }

    @Test
    fun shellRmParsesPlainLines() {
        val one = assertNotNull(NxShellRmParse.parse("rm a.txt"))
        assertEquals(listOf("a.txt"), one.paths)
        assertFalse(one.recursive)
        val many = assertNotNull(NxShellRmParse.parse("  rm -rf  build  dist/x  "))
        assertEquals(listOf("build", "dist/x"), many.paths)
        assertTrue(many.recursive)
        assertTrue(assertNotNull(NxShellRmParse.parse("rm -R d")).recursive)
        assertTrue(assertNotNull(NxShellRmParse.parse("rm --recursive --force d")).recursive)
        assertEquals(listOf("-weird"), assertNotNull(NxShellRmParse.parse("rm -- -weird")).paths)
        assertFalse(assertNotNull(NxShellRmParse.parse("rm -f -v a b")).recursive)
    }

    @Test
    fun shellRmLeavesAnythingUncertainToTheOldApproval() {
        for (line in listOf(
            "rm *.log", "rm a; rm b", "rm a && ls", "rm a | cat", "rm \$HOME/x", "rm ~/x", "rm 'a b'", "rm \"a b\"", "rm `x`", "rm a > o",
            "rm -rf", "rm", "rm --no-preserve-root /", "rm -x a", "rm --interactive a", "rm a\nrm b", "rm {a,b}", "rm a?", "rm [ab]",
            "ls -l", "rmdir a", "xrm a", "echo rm a", "sudo rm a",
        )) {
            assertNull(NxShellRmParse.parse(line), line)
        }
    }

    @Test
    fun catalogMarksTheServerCommandsReady() {
        for (name in listOf("node", "py", "install")) {
            val entry = NxCatalog.all.first { it.name == name }
            assertTrue(entry.ready, name)
            assertEquals(NxNeed.Vps, entry.need, name)
        }
        val off = EnvFacts(VpsState.Disconnected, true)
        val on = EnvFacts(VpsState.Connected, true)
        for (name in listOf("node", "py", "install")) {
            assertTrue(NxPick.forCommand(name, off) is NxChoice.Unavailable, name)
            assertTrue(NxPick.forCommand(name, on) is NxChoice.Use, name)
        }
        assertTrue(NxCatalog.all.none { !it.ready })
    }
}

package com.uplink.app

import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class NxStageOneTests {
    private fun box(seed: Int = 1) = SecretBox(SecretKeySpec(ByteArray(32) { (it + seed).toByte() }, "AES"))

    @Test
    fun secretRoundTrip() {
        val b = box()
        val sealed = b.seal("ai:key:1", "sk-test-123")
        assertTrue(SecretBox.isSealed(sealed))
        assertTrue(!sealed.contains("sk-test-123"))
        val back = b.open("ai:key:1", sealed)
        assertTrue(back is SecretRead.Sealed && back.value == "sk-test-123")
    }

    @Test
    fun secretLegacyPlainIsReadable() {
        val back = box().open("ai:key:1", "sk-old-plain")
        assertTrue(back is SecretRead.Plain && back.value == "sk-old-plain")
    }

    @Test
    fun secretSealIsRandomized() {
        val b = box()
        assertNotEquals(b.seal("x", "same"), b.seal("x", "same"))
    }

    @Test
    fun secretTamperedIsRejected() {
        val b = box()
        val sealed = b.seal("x", "value")
        val last = sealed.last()
        val flipped = sealed.dropLast(1) + (if (last == '0') '1' else '0')
        assertTrue(b.open("x", flipped) === SecretRead.Corrupt)
    }

    @Test
    fun secretTruncatedAndGarbageAreRejected() {
        val b = box()
        assertTrue(b.open("x", SecretBox.PREFIX + "abcd") === SecretRead.Corrupt)
        assertTrue(b.open("x", SecretBox.PREFIX + "zz") === SecretRead.Corrupt)
        assertTrue(b.open("x", SecretBox.PREFIX + "abc") === SecretRead.Corrupt)
    }

    @Test
    fun secretWrongLabelOrKeyIsRejected() {
        val sealed = box().seal("ai:key:1", "value")
        assertTrue(box().open("ai:key:2", sealed) === SecretRead.Corrupt)
        assertTrue(box(9).open("ai:key:1", sealed) === SecretRead.Corrupt)
    }

    @Test
    fun secretSealCheckedMatches() {
        val b = box()
        val sealed = b.sealChecked("x", "pässwörd ✓")
        val back = b.open("x", sealed)
        assertTrue(back is SecretRead.Sealed && back.value == "pässwörd ✓")
    }

    @Test
    fun deleteRefusesRoots() {
        assertEquals(DeleteRefusal.FilesystemRoot, DeletePolicy.refusal("/"))
        assertEquals(DeleteRefusal.StorageRoot, DeletePolicy.refusal("/storage"))
        assertEquals(DeleteRefusal.StorageRoot, DeletePolicy.refusal("/storage/emulated"))
        assertEquals(DeleteRefusal.StorageRoot, DeletePolicy.refusal("/storage/emulated/0"))
        assertEquals(DeleteRefusal.StorageRoot, DeletePolicy.refusal("/storage/emulated/0/"))
        assertEquals(DeleteRefusal.StorageRoot, DeletePolicy.refusal("/sdcard"))
        assertEquals(DeleteRefusal.StorageRoot, DeletePolicy.refusal("/storage/self/primary"))
        assertEquals(DeleteRefusal.StorageRoot, DeletePolicy.refusal("/storage/1234-ABCD"))
    }

    @Test
    fun deleteRefusesMediaFolders() {
        for (n in listOf("Download", "DCIM", "Documents", "Pictures", "Movies", "Music", "Android")) {
            assertEquals(DeleteRefusal.MediaFolder, DeletePolicy.refusal("/storage/emulated/0/$n"), n)
        }
        assertEquals(DeleteRefusal.MediaFolder, DeletePolicy.refusal("/sdcard/download"))
        assertEquals(DeleteRefusal.MediaFolder, DeletePolicy.refusal("/storage/emulated/0/Download/"))
    }

    @Test
    fun deleteResolvesDotDotTricks() {
        assertEquals(DeleteRefusal.StorageRoot, DeletePolicy.refusal("/storage/emulated/0/Download/.."))
        assertEquals(DeleteRefusal.FilesystemRoot, DeletePolicy.refusal("/storage/emulated/0/../../.."))
        assertEquals(DeleteRefusal.MediaFolder, DeletePolicy.refusal("/storage/emulated/0/x/../DCIM"))
    }

    @Test
    fun deleteAllowsOrdinaryPaths() {
        assertNull(DeletePolicy.refusal("/storage/emulated/0/Download/file.txt"))
        assertNull(DeletePolicy.refusal("/storage/emulated/0/Download/sub"))
        assertNull(DeletePolicy.refusal("/storage/emulated/0/myproject"))
        assertNull(DeletePolicy.refusal("/data/user/0/com.uplink.app/files/home/x"))
        assertNull(DeletePolicy.refusal("/home/user/Download"))
    }

    @Test
    fun deleteConfirmNamesAreExplicit() {
        val names = DeletePolicy.confirmNames(listOf("/sdcard/p/a.txt", "/storage/emulated/0/p/b.txt", "/sdcard/p/a.txt", "/storage/emulated/0/p/c.txt"))
        assertEquals(listOf("/storage/emulated/0/p/a.txt", "/storage/emulated/0/p/b.txt", "/storage/emulated/0/p/c.txt"), names)
    }

    @Test
    fun deleteConfirmRules() {
        assertTrue(DeletePolicy.needsConfirm("rm", false))
        assertTrue(DeletePolicy.needsConfirm("rmdir", false))
        assertTrue(DeletePolicy.needsConfirm("mv", true))
        assertTrue(!DeletePolicy.needsConfirm("mv", false))
        assertTrue(!DeletePolicy.needsConfirm("cp", true))
    }

    @Test
    fun catalogCoversAllPlannedCommands() {
        val names = NxCatalog.all.map { it.name }.toSet()
        val wanted = setOf(
            "help", "read", "ls", "find", "grep", "sh", "web", "text", "js", "env", "du", "tree", "stat", "head", "tail",
            "hash", "diff", "json", "write", "edit", "mkdir", "cp", "mv", "rm", "zip", "unzip", "fetch", "node", "py", "install",
        )
        assertEquals(wanted, names)
        assertEquals(NxCatalog.all.size, names.size)
    }

    @Test
    fun catalogReadyMatchesDispatch() {
        assertEquals(setOf("help", "env", "read", "ls", "find", "grep", "sh", "web", "text", "js", "du", "tree", "stat", "head", "tail", "hash", "diff", "json", "write", "edit", "mkdir", "cp", "mv", "rm", "zip", "unzip", "fetch", "node", "py", "install"), NxCatalog.readyNames().toSet())
        assertTrue(NxCatalog.all.filter { it.need == NxNeed.Vps }.all { it.ready })
    }

    @Test
    fun envParsesVpsProbe() {
        val p = VpsProbe.parse("os=Ubuntu 24.04 LTS\nkernel=Linux 6.8\nsh=/usr/bin/dash\nbash=GNU bash, version 5.2\nhas.du=1\nhas.tar=1\nhas.zip=0\nhas.node=0\nhas.python3=1\nhas.git=1\n")
        assertEquals("Ubuntu 24.04 LTS", p.os)
        assertEquals("GNU bash, version 5.2", p.shell)
        assertEquals(true, p.tools["du"])
        assertEquals(false, p.tools["zip"])
        assertEquals(false, p.tools["node"])
        assertEquals(VpsProbe.TOOLS.toSet(), p.tools.keys)
    }

    @Test
    fun envParseFallsBackWhenFieldsMissing() {
        val p = VpsProbe.parse("kernel=Linux 5.15\nsh=/bin/busybox\n")
        assertEquals("Linux 5.15", p.os)
        assertEquals("busybox", p.shell)
        assertTrue(p.tools.values.all { !it })
    }

    @Test
    fun envBlocksVpsCommandsWhenNotConnected() {
        val off = EnvFacts(VpsState.Disconnected, true)
        assertTrue(NxPick.forCommand("install", off) is NxChoice.Unavailable)
        assertTrue(NxPick.forCommand("node", EnvFacts(VpsState.NotConfigured, true)) is NxChoice.Unavailable)
    }

    @Test
    fun envBlocksWebWithoutInternet() {
        val c = NxPick.forCommand("web", EnvFacts(VpsState.NotConfigured, false))
        assertTrue(c is NxChoice.Unavailable && c.reason == "needs internet")
        assertTrue(NxPick.forCommand("web", EnvFacts(VpsState.NotConfigured, true)) is NxChoice.Use)
        assertTrue(NxPick.forCommand("text", EnvFacts(VpsState.NotConfigured, false)) is NxChoice.Use)
    }

    @Test
    fun envPickKnowsReadyAndUnknown() {
        val f = EnvFacts(VpsState.Connected, true)
        assertEquals("nx read", (NxPick.forCommand("read", f) as NxChoice.Use).command)
        assertTrue(NxPick.forCommand("write", f) is NxChoice.Unavailable)
        assertTrue(NxPick.forCommand("bogus", f).let { it is NxChoice.Unavailable && it.reason == "unknown action" })
    }

    @Test
    fun envCacheReusesUntilSignatureChanges() {
        val cache = NxEnvCache<String>()
        val a = EnvFacts(VpsState.Connected, true)
        val sigA = NxPick.signature(a, "s1")
        assertEquals("v1", cache.get(sigA) { "v1" })
        assertEquals("v1", cache.get(sigA) { "v2" })
        assertEquals(1, cache.builds)
        assertEquals("v3", cache.get(NxPick.signature(EnvFacts(VpsState.Disconnected, true), "s1")) { "v3" })
        assertEquals("v4", cache.get(NxPick.signature(EnvFacts(VpsState.Disconnected, false), "s1")) { "v4" })
        assertEquals("v5", cache.get(NxPick.signature(EnvFacts(VpsState.Connected, false), "s2")) { "v5" })
        assertEquals(4, cache.builds)
    }
}

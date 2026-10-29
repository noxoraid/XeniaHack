package com.uplink.app

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class NxStageFiveTests {
    private val say = NxPlain

    private fun refused(url: String): String =
        assertFailsWith<NxFetchError>(url) { NxFetchUrl.check(url, say) }.message.orEmpty()

    private fun accepted(url: String): NxFetchTarget = NxFetchUrl.check(url, say)

    @Test
    fun acceptanceLoopbackAndPrivateAddressesAreRefusedBeforeAnyConnection() {
        for (url in listOf("http://127.0.0.1/", "http://localhost/", "http://192.168.1.1/", "http://169.254.1.1/")) {
            refused(url)
        }
    }

    @Test
    fun privateRangesAreRefusedAtTheirEdges() {
        for (host in listOf("10.0.0.0", "10.255.255.255", "172.16.0.1", "172.31.255.255", "192.168.0.1", "169.254.169.254", "127.255.255.254", "0.0.0.0", "100.64.0.1", "224.0.0.1", "255.255.255.255")) {
            refused("http://$host/")
        }
    }

    @Test
    fun publicAddressesNextToPrivateRangesPass() {
        for (host in listOf("172.15.255.255", "172.32.0.1", "8.8.8.8", "192.169.0.1", "11.0.0.1", "100.63.255.255", "100.128.0.1")) {
            accepted("http://$host/")
        }
    }

    @Test
    fun alternateIpv4SpellingsAreRefused() {
        for (host in listOf("2130706433", "0x7f000001", "0177.0.0.1", "127.1", "0x7f.0.0.1", "017700000001", "3232235777", "0xc0.0xa8.1.1")) {
            refused("http://$host/")
        }
    }

    @Test
    fun numericLookingButInvalidHostsAreRefused() {
        refused("http://256.1.1.1/")
        refused("http://08.0.0.1/")
        refused("http://1.2.3.4.5/")
    }

    @Test
    fun alternateSpellingIsRewrittenToTheDottedForm() {
        assertEquals("8.8.8.8", accepted("http://0x08080808/").host)
        assertEquals("http://8.8.8.8/x", accepted("http://134744072/x").url)
    }

    @Test
    fun ipv6LoopbackAndLocalRangesAreRefused() {
        for (host in listOf("::1", "::", "fe80::1", "febf::1", "fec0::1", "fc00::1", "fd12:3456::1", "ff02::1", "::ffff:127.0.0.1", "::ffff:7f00:1", "::ffff:192.168.1.1", "64:ff9b::7f00:1", "2002:7f00:1::", "2001:0:1:2::3", "0:0:0:0:0:0:0:1")) {
            refused("http://[$host]/")
        }
    }

    @Test
    fun publicIpv6Passes() {
        val t = accepted("https://[2606:4700:4700::1111]/dns")
        assertEquals("[2606:4700:4700::1111]", t.host)
        assertTrue(t.literal)
        accepted("http://[::ffff:8.8.8.8]/")
    }

    @Test
    fun malformedIpv6IsRefused() {
        for (host in listOf("1:2:3", "1::2::3", ":::1", "12345::1", "1:2:3:4:5:6:7:8:9", "::g", "fe80::1%eth0", "1.2.3.4", ":1::")) {
            refused("http://[$host]/")
        }
        refused("http://[::1/")
        refused("http://[::1]x/")
    }

    @Test
    fun localNamesAreRefused() {
        for (host in listOf("localhost", "LOCALHOST", "localhost.", "app.localhost", "printer.local", "metadata.google.internal", "router.lan", "nas.home.arpa", "intranet", "router")) {
            refused("http://$host/")
        }
    }

    @Test
    fun onlyHttpAndHttpsPass() {
        for (url in listOf("ftp://example.com/a", "file:///etc/passwd", "gopher://example.com", "ws://example.com", "javascript:alert(1)", "data:text/html,hi", "example.com", "//example.com/x", "")) {
            refused(url)
        }
        assertEquals("http://example.com/", accepted("HTTP://Example.com").url)
        assertEquals("https://example.com/a", accepted("https://example.com/a").url)
    }

    @Test
    fun credentialsInTheAddressAreRefused() {
        refused("http://user:pass@example.com/")
        refused("http://example.com@127.0.0.1/")
        refused("http://example.com:80@127.0.0.1/")
    }

    @Test
    fun theHostIsTheOneBeforeSlashQuestionMarkOrHash() {
        refused("http://127.0.0.1#@example.com/")
        refused("http://127.0.0.1?@example.com/")
        refused("http://127.0.0.1/@example.com/")
        assertEquals("example.com", accepted("http://example.com#@127.0.0.1").host)
    }

    @Test
    fun backslashAndControlCharactersAreRefused() {
        refused("http://example.com\\@127.0.0.1/")
        refused("http://example.com/\u0000x")
        refused("http://example.com/\nx")
    }

    @Test
    fun portsMustBeValid() {
        refused("http://example.com:0/")
        refused("http://example.com:65536/")
        refused("http://example.com:99999/")
        refused("http://example.com:abc/")
        refused("http://example.com:123456/")
        assertEquals(8080, accepted("http://example.com:8080/").port)
        assertEquals("https://example.com:8443/", accepted("https://example.com:8443").url)
        assertEquals("http://example.com/", accepted("http://example.com:80/").url)
        assertEquals("https://example.com/", accepted("https://example.com:443/").url)
    }

    @Test
    fun trailingDotAndCaseAreNormalised() {
        val t = accepted("https://EXAMPLE.com./Path")
        assertEquals("example.com", t.host)
        assertEquals("https://example.com/Path", t.url)
    }

    @Test
    fun internationalNamesBecomePunycode() {
        assertEquals("xn--bcher-kva.example", accepted("http://b\u00fccher.example/").host)
    }

    @Test
    fun malformedNamesAreRefused() {
        refused("http://example..com/")
        refused("http://-bad.example.com/")
        refused("http://exa mple.com/")
        refused("http://exa_mple!.com/")
        refused("http:///path")
        refused("http://:80/")
    }

    @Test
    fun theFragmentIsDroppedAndTheTailIsEncoded() {
        assertEquals("http://example.com/a%20b?q=1%202", accepted("http://example.com/a b?q=1 2#frag").url)
        assertEquals("http://example.com/?x=1", accepted("http://example.com?x=1").url)
        assertEquals("http://example.com/p%27q", accepted("http://example.com/p'q").url)
        assertEquals("http://example.com/%C3%A9", accepted("http://example.com/\u00e9").url)
        assertEquals("http://example.com/a%20b", accepted("http://example.com/a%20b").url)
    }

    @Test
    fun checkingAnAcceptedAddressAgainChangesNothing() {
        for (url in listOf("https://Example.com/a b?x=1#h", "http://0x08080808:81/p'q", "https://[2606:4700:4700::1111]:8443/%C3%A9")) {
            val once = accepted(url).url
            assertEquals(once, accepted(once).url)
        }
    }

    @Test
    fun addressRangesAreCheckedOnRawResolvedBytes() {
        assertTrue(NxFetchAddress.blocked(byteArrayOf(127, 0, 0, 1)))
        assertTrue(NxFetchAddress.blocked(byteArrayOf(192.toByte(), 168.toByte(), 1, 1)))
        assertFalse(NxFetchAddress.blocked(byteArrayOf(8, 8, 8, 8)))
        assertTrue(NxFetchAddress.blocked(ByteArray(16).also { it[15] = 1 }))
        assertTrue(NxFetchAddress.blocked(ByteArray(3)))
        assertFalse(NxFetchAddress.blocked(NxFetchAddress.parseV6("2606:4700:4700::1111")!!))
    }

    @Test
    fun redirectsAreResolvedAndRevalidated() {
        val from = accepted("https://example.com/a/b")
        assertEquals("https://example.com/a/c", NxFetchRedirect.next(from, "c", say).url)
        assertEquals("https://example.com/x", NxFetchRedirect.next(from, "/x", say).url)
        assertEquals("https://other.example/y", NxFetchRedirect.next(from, "//other.example/y", say).url)
        assertEquals("https://other.example/z", NxFetchRedirect.next(from, "https://other.example/z", say).url)
        assertEquals("https://example.com/a/sp%20ace", NxFetchRedirect.next(from, "sp ace", say).url)
    }

    @Test
    fun redirectsToLocalTargetsAreRefused() {
        val from = accepted("https://example.com/")
        for (to in listOf("http://127.0.0.1/", "https://localhost/", "https://192.168.1.1/x", "//169.254.169.254/latest", "https://[::1]/", "ftp://example.com/", "file:///etc/passwd", "https://u:p@example.com/")) {
            assertFailsWith<NxFetchError>(to) { NxFetchRedirect.next(from, to, say) }
        }
    }

    @Test
    fun redirectFromHttpsToHttpIsRefusedButHttpToHttpsPasses() {
        assertTrue(assertFailsWith<NxFetchError> { NxFetchRedirect.next(accepted("https://example.com/"), "http://example.com/", say) }.message.orEmpty().contains("https to http"))
        assertEquals("https://example.com/", NxFetchRedirect.next(accepted("http://example.com/"), "https://example.com/", say).url)
        assertEquals("http://example.org/", NxFetchRedirect.next(accepted("http://example.com/"), "http://example.org/", say).url)
    }

    @Test
    fun emptyRedirectIsRefused() {
        assertFailsWith<NxFetchError> { NxFetchRedirect.next(accepted("https://example.com/"), "  ", say) }
        assertTrue(NxFetchRedirect.isRedirect(301) && NxFetchRedirect.isRedirect(308))
        assertFalse(NxFetchRedirect.isRedirect(304) || NxFetchRedirect.isRedirect(200))
    }

    @Test
    fun domainApprovalIsPerExactHost() {
        NxFetchDomains.clear()
        assertFalse(NxFetchDomains.isAllowed("example.com"))
        NxFetchDomains.allow("example.com")
        assertTrue(NxFetchDomains.isAllowed("example.com"))
        assertFalse(NxFetchDomains.isAllowed("www.example.com"))
        assertFalse(NxFetchDomains.isAllowed("example.org"))
        NxFetchDomains.clear()
        assertFalse(NxFetchDomains.isAllowed("example.com"))
    }

    private fun text(html: String): String = NxHtmlText.convert(html).text

    @Test
    fun scriptsStylesAndCommentsAreDropped() {
        val html = "<p>a</p><script>var x = '</div>';</script><style>p{color:red}</style><!-- hidden <b>x</b> --><noscript>no</noscript><p>b</p>"
        assertEquals("a\nb", text(html))
    }

    @Test
    fun uppercaseAndUnclosedScriptAreHandled() {
        assertEquals("a", text("a<SCRIPT>alert(1)</SCRIPT>"))
        assertEquals("a", text("a<script>never closed <p>b</p>"))
    }

    @Test
    fun entitiesAreDecoded() {
        assertEquals("a & b < c > d \"e\" 'f' A A \u2019 \u00a9", text("<p>a &amp; b &lt; c &gt; d &quot;e&quot; &#39;f&#39; &#65; &#x41; &rsquo; &copy;</p>"))
    }

    @Test
    fun unknownAndBrokenEntitiesStayAsTyped() {
        assertEquals("a &zzz; b & c &#99999999; d", text("<p>a &zzz; b & c &#99999999; d</p>"))
        assertEquals("&#xD800;", text("&#xD800;"))
    }

    @Test
    fun blockTagsBreakLinesAndInlineTagsDoNot() {
        assertEquals("one\ntwo\nthree four", text("<div>one</div><div>two</div><p>three <b>four</b></p>"))
        assertEquals("ab", text("<span>a</span><span>b</span>"))
        assertEquals("a b", text("<span>a</span> <span>b</span>"))
        assertEquals("a\nb", text("a<br>b"))
    }

    @Test
    fun listsAndHeadingsKeepTheirShape() {
        assertEquals("# Title\n- one\n- two\n### Small", text("<h1>Title</h1><ul><li>one</li><li>two</li></ul><h3>Small</h3>"))
    }

    @Test
    fun tableCellsAreSeparatedWithoutPipes() {
        val out = text("<table><tr><td>a</td><td>b</td></tr><tr><td>c</td><td>d</td></tr></table>")
        assertEquals("a b\nc d", out)
        assertFalse(out.contains('|'))
    }

    @Test
    fun preformattedTextKeepsItsWhitespace() {
        assertEquals("before\nline1\n  line2\nafter", text("<p>before</p><pre>line1\n  line2</pre><p>after</p>"))
    }

    @Test
    fun whitespaceRunsCollapse() {
        assertEquals("a b c", text("  <p>  a \n\n   b\t\tc  </p>  "))
    }

    @Test
    fun titleIsTakenOutOfTheBody() {
        val page = NxHtmlText.convert("<html><head><title> My &amp; Page\n </title><meta charset=utf-8></head><body><p>Hello</p></body></html>")
        assertEquals("My & Page", page.title)
        assertEquals("Hello", page.text)
    }

    @Test
    fun attributeValuesWithAngleBracketsDoNotLeak() {
        assertEquals("x", text("<a href=\"/p?a>b\" title='>'>x</a>"))
    }

    @Test
    fun aLiteralLessThanIsKept() {
        assertEquals("1 < 2 and 3 > 2", text("<p>1 < 2 and 3 > 2</p>"))
    }

    @Test
    fun truncatedAndGarbageInputDoesNotLoopOrThrow() {
        text("<p>unfinished <a href=\"x")
        text("<<<<>>>><//><!><?>")
        text("<")
        text("&")
        text("")
        assertEquals("", text("<script>"))
    }

    @Test
    fun largeInputIsLinear() {
        val html = "<div><p>word </p></div>".repeat(60_000)
        val started = System.nanoTime()
        val out = text(html)
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue(out.startsWith("word\nword"))
        assertTrue(ms < 3000, "took $ms ms")
    }

    @Test
    fun contentKindFollowsTheDeclaredType() {
        assertEquals(NxFetchKind.Html, NxFetchBody.kind("text/html; charset=utf-8", ByteArray(0)))
        assertEquals(NxFetchKind.Html, NxFetchBody.kind("application/xhtml+xml", ByteArray(0)))
        assertEquals(NxFetchKind.Text, NxFetchBody.kind("application/json", ByteArray(0)))
        assertEquals(NxFetchKind.Text, NxFetchBody.kind("application/vnd.api+json", ByteArray(0)))
        assertEquals(NxFetchKind.Text, NxFetchBody.kind("text/plain", ByteArray(0)))
        assertEquals(NxFetchKind.Text, NxFetchBody.kind("application/rss+xml", ByteArray(0)))
        assertEquals(NxFetchKind.Binary, NxFetchBody.kind("image/png", ByteArray(0)))
        assertEquals(NxFetchKind.Binary, NxFetchBody.kind("application/pdf", ByteArray(0)))
        assertEquals(NxFetchKind.Binary, NxFetchBody.kind("application/zip", ByteArray(0)))
    }

    @Test
    fun missingTypeIsSniffedFromTheBytes() {
        assertEquals(NxFetchKind.Html, NxFetchBody.kind(null, "  <!DOCTYPE html><p>".toByteArray()))
        assertEquals(NxFetchKind.Html, NxFetchBody.kind("", "<HTML>".toByteArray()))
        assertEquals(NxFetchKind.Text, NxFetchBody.kind(null, "plain words".toByteArray()))
        assertEquals(NxFetchKind.Binary, NxFetchBody.kind(null, byteArrayOf(0x50, 0x4b, 0, 0)))
        assertEquals(NxFetchKind.Binary, NxFetchBody.kind("application/octet-stream", byteArrayOf(1, 0, 2)))
    }

    @Test
    fun binaryTypesAreRefusedFromTheHeaderAlone() {
        assertTrue(NxFetchBody.declaredBinary("image/jpeg"))
        assertTrue(NxFetchBody.declaredBinary("application/pdf"))
        assertFalse(NxFetchBody.declaredBinary("text/html"))
        assertFalse(NxFetchBody.declaredBinary(null))
        assertFalse(NxFetchBody.declaredBinary("application/octet-stream"))
    }

    @Test
    fun charsetComesFromHeaderThenBomThenMeta() {
        val latin = "caf\u00e9".toByteArray(Charsets.ISO_8859_1)
        assertEquals("caf\u00e9", NxFetchBody.decode(latin, "text/plain; charset=ISO-8859-1"))
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "caf\u00e9".toByteArray(Charsets.UTF_8)
        assertEquals("caf\u00e9", NxFetchBody.decode(bom, "text/plain"))
        val meta = "<meta charset=\"windows-1252\"><p>caf\u00e9</p>".toByteArray(Charsets.ISO_8859_1)
        assertTrue(NxFetchBody.decode(meta, "text/html").contains("caf\u00e9"))
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "hi".toByteArray(Charsets.UTF_16LE)
        assertTrue(NxFetchBody.decode(utf16, null).endsWith("hi"))
    }

    @Test
    fun unknownCharsetFallsBackToUtf8() {
        assertEquals("h\u00e9", NxFetchBody.decode("h\u00e9".toByteArray(Charsets.UTF_8), "text/plain; charset=nope-9000"))
        assertEquals("h\u00e9", NxFetchBody.decode("h\u00e9".toByteArray(Charsets.UTF_8), null))
    }

    @Test
    fun shortPageComesBackWhole() {
        assertEquals("hello", NxFetchPage.slice("hello", 0, 100, "https://e.com/", say))
        assertEquals("hello", NxFetchPage.slice("hello", 0, 5, "https://e.com/", say))
    }

    @Test
    fun longPageIsCutAtALineAndNamesHowToContinue() {
        val text = (1..400).joinToString("\n") { "line number $it" }
        val first = NxFetchPage.slice(text, 0, 1000, "https://e.com/p", say)
        assertTrue(first.contains("[cut at character"), first)
        assertTrue(first.contains("nx fetch 'https://e.com/p'"), first)
        val cut = Regex("cut at character (\\d+) of (\\d+)").find(first)!!.groupValues
        assertEquals(text.length, cut[2].toInt())
        assertTrue(first.substringBefore("\n[cut").length <= 1000)
        assertEquals(text.substring(0, cut[1].toInt()).trimEnd(), first.substringBefore("\n[cut"))
    }

    @Test
    fun walkingThroughEveryPartRebuildsTheWholeText() {
        val text = (1..500).joinToString("\n") { "row $it with some text" }
        val built = StringBuilder()
        var offset = 0
        var guard = 0
        while (true) {
            val part = NxFetchPage.slice(text, offset, 700, "https://e.com/", say)
            val at = part.lastIndexOf("\n[cut at character ")
            if (at < 0) {
                built.append(part)
                break
            }
            built.append(part.substring(0, at)).append('\n')
            offset = Regex("cut at character (\\d+)").find(part.substring(at))!!.groupValues[1].toInt()
            assertTrue(++guard < 200)
        }
        assertEquals(text, built.toString())
    }

    @Test
    fun offsetPastTheEndIsRefusedWithTheLength() {
        val msg = assertFailsWith<NxFetchError> { NxFetchPage.slice("abc", 3, 10, "https://e.com/", say) }.message.orEmpty()
        assertTrue(msg.contains("3 characters"), msg)
    }

    @Test
    fun emptyPageSaysSo() {
        assertEquals("the page has no readable text", NxFetchPage.slice("", 0, 10, "https://e.com/", say))
    }

    @Test
    fun aCutNeverSplitsASurrogatePair() {
        val text = "a".repeat(9) + "\uD83D\uDE00" + "b".repeat(20)
        val part = NxFetchPage.slice(text, 0, 10, "https://e.com/", say)
        assertFalse(part.any { Character.isHighSurrogate(it) && !part.contains("\uD83D\uDE00") })
    }

    @Test
    fun fetchIsReadyAndNeedsInternet() {
        val entry = NxCatalog.all.first { it.name == "fetch" }
        assertTrue(entry.ready)
        assertEquals(NxNeed.Internet, entry.need)
        assertEquals("nx fetch <url> [offset]", entry.usage)
        assertTrue(NxPick.forCommand("fetch", EnvFacts(VpsState.NotConfigured, true)) is NxChoice.Use)
        val offline = NxPick.forCommand("fetch", EnvFacts(VpsState.NotConfigured, false))
        assertTrue(offline is NxChoice.Unavailable && offline.reason == "needs internet")
    }
}

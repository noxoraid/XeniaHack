package com.uplink.app

import android.net.Uri

object WebBlock {
    private val hosts = hashSetOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "googletagservices.com", "google-analytics.com",
        "adservice.google.com", "adnxs.com", "adsrvr.org", "adform.net", "criteo.com", "criteo.net", "taboola.com",
        "outbrain.com", "revcontent.com", "mgid.com", "popads.net", "popcash.net", "propellerads.com", "adsterra.com",
        "exoclick.com", "exosrv.com", "juicyads.com", "trafficjunky.com", "trafficfactory.biz", "clickadu.com",
        "hilltopads.net", "adcash.com", "a-ads.com", "bidvertiser.com", "pubmatic.com", "rubiconproject.com", "openx.net",
        "casalemedia.com", "indexww.com", "smartadserver.com", "amazon-adsystem.com", "moatads.com", "adsafeprotected.com",
        "scorecardresearch.com", "quantserve.com", "serving-sys.com", "2mdn.net", "advertising.com", "yieldmo.com",
        "sharethrough.com", "teads.tv", "media.net", "adroll.com", "rlcdn.com", "bluekai.com", "krxd.net", "demdex.net",
        "everesttech.net", "tapad.com", "zedo.com", "infolinks.com", "adskeeper.com", "onclkds.com", "yllix.com",
        "ads.yahoo.com", "adtng.com", "adxpansion.com", "ero-advertising.com", "tsyndicate.com", "realsrv.com",
        "syndication.exoclick.com", "pushwoosh.com", "onesignal.com", "ad-maven.com", "admaven.com", "monetag.com",
        "richpush.co", "mookie1.com", "contextweb.com", "lijit.com", "sovrn.com", "33across.com", "triplelift.com",
        "gumgum.com", "undertone.com", "spotxchange.com", "springserve.com", "adsymptotic.com", "bidswitch.net",
        "smaato.net", "inmobi.com", "applovin.com", "unityads.unity3d.com", "ads.mopub.com", "mopub.com",
        "hotjar.com", "mouseflow.com", "crazyegg.com", "fullstory.com",
    )

    private val prefixes = listOf("ads.", "adserver.", "adservice.", "adsense.", "ad.", "banners.")

    fun blocks(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        for (p in prefixes) if (host.startsWith(p)) return true
        var h: String = host
        while (true) {
            if (h in hosts) return true
            val dot = h.indexOf('.')
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return false
            h = h.substring(dot + 1)
        }
    }

    private val mediaExt = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "avif", "bmp", "svg", "ico",
        "mp4", "webm", "mov", "m4v", "m3u8", "ts",
        "mp3", "ogg", "wav", "aac", "m4a", "opus",
    )

    fun isMedia(url: Uri, headers: Map<String, String>): Boolean {
        val accept = headers.entries.firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value.orEmpty()
        if (accept.startsWith("image/") || accept.startsWith("video/") || accept.startsWith("audio/")) return true
        val ext = url.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return ext in mediaExt
    }

    private const val HIDE = "ins.adsbygoogle,.adsbygoogle,[id^=\"google_ads\"],[id^=\"div-gpt-ad\"],[id^=\"aswift\"]," +
        "[id*=\"ad-slot\"],[class*=\"ad-slot\"],[class*=\"ad-banner\"],[class*=\"adbox\"],[class^=\"ad-\"],[id^=\"ad_\"]," +
        ".advert,.advertisement,.ad-container,.ad-wrapper,.sponsored-ad,#advert," +
        "iframe[src*=\"doubleclick.net\"],iframe[src*=\"googlesyndication\"],iframe[src*=\"adnxs\"]," +
        "a[href*=\"doubleclick.net\"],div[data-ad],div[data-google-query-id]"

    val cosmetic: String =
        "(function(){if(document.getElementById('nx-adhide'))return;var s=document.createElement('style');" +
            "s.id='nx-adhide';s.textContent='$HIDE{display:none!important}';" +
            "(document.head||document.documentElement).appendChild(s)})()"
}

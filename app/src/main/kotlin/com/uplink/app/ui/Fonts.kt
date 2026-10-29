package com.uplink.app

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File

private const val GF = "https://raw.githubusercontent.com/google/fonts/main/ofl/"

private fun variable(path: String): List<Pair<FontWeight, String>> =
    listOf(FontWeight.Normal to GF + path, FontWeight.Bold to GF + path)

class FontChoice(
    val key: String,
    val label: String,
    fallback: FontFamily,
    private val sources: List<Pair<FontWeight, String>> = emptyList(),
) {
    var family: FontFamily by mutableStateOf(fallback)
        private set

    @Volatile private var face: Typeface? = null

    val remote: Boolean get() = sources.isNotEmpty()

    fun typeface(ctx: Context): Typeface = face ?: Typeface.MONOSPACE

    fun fetch(ctx: Context) {
        if (!remote) return
        val loaded = ArrayList<Pair<FontWeight, File>>()
        for ((weight, url) in sources) {
            val file = GoogleFontStore.ensure(ctx, url) ?: return
            loaded.add(weight to file)
        }
        val regular = loaded.firstOrNull { it.first == FontWeight.Normal }?.second ?: loaded.first().second
        Handler(Looper.getMainLooper()).post {
            face = try { Typeface.createFromFile(regular) } catch (_: Exception) { null }
            family = FontFamily(loaded.map { (w, f) -> Font(f, w) })
        }
    }
}

object NxFonts {
    val ui = listOf(
        FontChoice("poppins", "Poppins", FontFamily.SansSerif, listOf(
            FontWeight.Normal to GF + "poppins/Poppins-Regular.ttf",
            FontWeight.Medium to GF + "poppins/Poppins-Medium.ttf",
            FontWeight.Bold to GF + "poppins/Poppins-Bold.ttf",
        )),
        FontChoice("instrument", "Instrument Sans", FontFamily.SansSerif, variable("instrumentsans/InstrumentSans%5Bwdth%2Cwght%5D.ttf")),
        FontChoice("work", "Work Sans", FontFamily.SansSerif, variable("worksans/WorkSans%5Bwght%5D.ttf")),
        FontChoice("outfit", "Outfit", FontFamily.SansSerif, variable("outfit/Outfit%5Bwght%5D.ttf")),
        FontChoice("bricolage", "Bricolage Grotesque", FontFamily.SansSerif, variable("bricolagegrotesque/BricolageGrotesque%5Bopsz%2Cwdth%2Cwght%5D.ttf")),
        FontChoice("system", "System", FontFamily.SansSerif),
    )

    val mono = listOf(
        FontChoice("jetbrains", "JetBrains Mono", FontFamily.Monospace, variable("jetbrainsmono/JetBrainsMono%5Bwght%5D.ttf")),
        FontChoice("geist", "Geist Mono", FontFamily.Monospace, variable("geistmono/GeistMono%5Bwght%5D.ttf")),
        FontChoice("plex", "IBM Plex Mono", FontFamily.Monospace, listOf(
            FontWeight.Normal to GF + "ibmplexmono/IBMPlexMono-Regular.ttf",
            FontWeight.Bold to GF + "ibmplexmono/IBMPlexMono-Bold.ttf",
        )),
        FontChoice("dm", "DM Mono", FontFamily.Monospace, listOf(
            FontWeight.Normal to GF + "dmmono/DMMono-Regular.ttf",
        )),
        FontChoice("system", "System", FontFamily.Monospace),
    )
}

object FontPrefs {
    private const val FILE = "noxora_ui"

    var ui by mutableStateOf(NxFonts.ui[0])
        private set
    var mono by mutableStateOf(NxFonts.mono[0])
        private set

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        ui = NxFonts.ui.firstOrNull { it.key == p.getString("ui", null) } ?: NxFonts.ui[0]
        mono = NxFonts.mono.firstOrNull { it.key == p.getString("mono", null) } ?: NxFonts.mono[0]
        fetchRemote(ctx.applicationContext)
    }

    private fun fetchRemote(app: Context) {
        Thread {
            (NxFonts.ui + NxFonts.mono).distinctBy { it.key }.forEach { it.fetch(app) }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun pickUi(ctx: Context, choice: FontChoice) {
        ui = choice
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("ui", choice.key).apply()
    }

    fun pickMono(ctx: Context, choice: FontChoice) {
        mono = choice
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("mono", choice.key).apply()
    }
}

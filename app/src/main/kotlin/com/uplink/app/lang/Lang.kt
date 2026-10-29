package com.uplink.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

class Language(val code: String, val label: String, val words: Map<String, String>)

object Lang {
    val all: List<Language> = listOf(
        Language("en", "English", wordsEn),
        Language("id", "Indonesia", wordsId),
    )

    var choice by mutableStateOf("system")

    val current: Language
        get() {
            val wanted = if (choice == "system") deviceCode() else choice
            return all.firstOrNull { it.code == wanted } ?: all.first()
        }

    fun load(ctx: Context) {
        choice = Cfg.language(ctx)
    }

    fun set(ctx: Context, code: String) {
        choice = code
        Cfg.setLanguage(ctx, code)
    }

    fun locale(): Locale = Locale.forLanguageTag(current.code)

    private fun deviceCode(): String {
        val code = Locale.getDefault().language
        return if (code == "in") "id" else code
    }
}

fun tr(key: String, vararg args: Any?): String {
    var text = Lang.current.words[key] ?: key
    for ((i, arg) in args.withIndex()) text = text.replace("{$i}", arg.toString())
    return text
}

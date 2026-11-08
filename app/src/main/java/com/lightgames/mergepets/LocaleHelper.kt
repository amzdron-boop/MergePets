package com.lightgames.mergepets

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** In-app language choice: "" = follow system, "en", or "ru". Default resources are English. */
object LocaleHelper {
    private const val PREFS = "settings"
    private const val KEY = "lang"

    fun get(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    fun set(ctx: Context, lang: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, lang).apply()
    }

    fun wrap(base: Context): Context {
        val lang = get(base)
        if (lang.isEmpty()) return base
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(locale)
        return base.createConfigurationContext(cfg)
    }
}

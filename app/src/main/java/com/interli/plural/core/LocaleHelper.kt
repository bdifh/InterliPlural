package com.interli.plural.core

import android.content.Context
import android.content.res.Configuration
import java.util.*

object LocaleHelper {
    fun getLocale(context: Context): String {
        val sharedPref = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        return sharedPref.getString("app_language", "en") ?: "en"
    }
    fun wrapContext(context: Context): Context {
        val lang = getLocale(context)
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
    fun applyLocale(context: Context) {
        val lang = getLocale(context)
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}

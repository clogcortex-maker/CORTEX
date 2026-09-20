package com.cortex.app.ui

import android.content.Context

object Themes {

    data class ThemeDef(val key: String, val name: String, val styleRes: Int)

    val ALL = listOf(
        ThemeDef("cortex", "Cortex (default)", com.cortex.app.R.style.Theme_CORTEX_Default),
        ThemeDef("amoled", "AMOLED Black", com.cortex.app.R.style.Theme_CORTEX_Amoled),
        ThemeDef("ocean", "Ocean", com.cortex.app.R.style.Theme_CORTEX_Ocean),
        ThemeDef("forest", "Forest", com.cortex.app.R.style.Theme_CORTEX_Forest),
        ThemeDef("sunset", "Sunset", com.cortex.app.R.style.Theme_CORTEX_Sunset),
        ThemeDef("crimson", "Crimson", com.cortex.app.R.style.Theme_CORTEX_Crimson),
        ThemeDef("daylight", "Daylight", com.cortex.app.R.style.Theme_CORTEX_Daylight)
    )

    private const val PREFS = "cortex_prefs"
    private const val KEY = "theme"

    fun currentKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "cortex") ?: "cortex"

    fun currentStyleRes(context: Context): Int =
        ALL.firstOrNull { it.key == currentKey(context) }?.styleRes ?: ALL[0].styleRes

    fun apply(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, key).apply()
    }
}

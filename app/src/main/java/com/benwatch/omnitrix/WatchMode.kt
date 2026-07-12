package com.benwatch.omnitrix

import android.content.Context

/**
 * The three faces the dial can show. Cycled with a long-press on the core symbol.
 * NORMAL  -> green, default idle look
 * DNA_SCAN -> yellow, plays a scanning sweep animation (like scanning for new alien DNA)
 * LOW_POWER -> red/dim, minimal animation to save battery
 */
enum class WatchMode(val label: String) {
    NORMAL("NORMAL"),
    DNA_SCAN("DNA SCAN"),
    LOW_POWER("LOW POWER");

    fun next(): WatchMode = when (this) {
        NORMAL -> DNA_SCAN
        DNA_SCAN -> LOW_POWER
        LOW_POWER -> NORMAL
    }
}

/** Tiny persistence helper so the selected mode survives app restarts. */
object ModeManager {
    private const val PREFS = "omnitrix_prefs"
    private const val KEY_MODE = "watch_mode"

    fun save(context: Context, mode: WatchMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    fun load(context: Context): WatchMode {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, WatchMode.NORMAL.name)
        return try {
            WatchMode.valueOf(name ?: WatchMode.NORMAL.name)
        } catch (e: IllegalArgumentException) {
            WatchMode.NORMAL
        }
    }
}

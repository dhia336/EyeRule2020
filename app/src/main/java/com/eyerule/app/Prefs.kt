package com.eyerule.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Every SharedPreferences key used across the app lives here. Nothing else
 * should write a key string by hand - typo one in a random file and a
 * setting just silently stops working.
 */
object Prefs {
    private const val FILE_NAME = "eyerule_prefs"

    const val INTERVAL_MINUTES = "interval_minutes"
    const val BREAK_SECONDS = "break_seconds"
    const val RUNNING = "running"
    const val NEXT_BREAK_AT_ELAPSED = "next_break_at_elapsed"
    const val PLAY_SOUND = "play_sound"
    const val UNSKIPPABLE = "unskippable"
    const val RESTART_ON_UNLOCK = "restart_on_unlock"

    const val DEFAULT_INTERVAL_MINUTES = 20
    const val DEFAULT_BREAK_SECONDS = 20

    fun from(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
}

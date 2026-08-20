package com.eyerule.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock

object AlarmScheduler {

    private const val REQUEST_CODE = 1001

    /** Starts the whole cycle: arms the first break and starts the notification service. */
    fun schedule(context: Context, prefs: SharedPreferences, intervalMinutes: Int) {
        prefs.edit()
            .putInt(Prefs.INTERVAL_MINUTES, intervalMinutes)
            .putBoolean(Prefs.RUNNING, true)
            .apply()

        armNextAlarm(context, prefs, intervalMinutes * 60_000L)
        context.startService(Intent(context, CountdownService::class.java))
    }

    fun cancel(context: Context, prefs: SharedPreferences) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(buildPendingIntent(context))
        prefs.edit().putBoolean(Prefs.RUNNING, false).apply()
        context.stopService(Intent(context, CountdownService::class.java))
    }

    fun isRunning(prefs: SharedPreferences): Boolean = prefs.getBoolean(Prefs.RUNNING, false)

    /**
     * Re-arms a single, exact one-shot alarm for the next break. Called once when the
     * rule starts, and again from BreakOverlayService only after the pause screen has
     * actually finished - so the interval is measured from "break ended" to "break ended",
     * not from "break fired" to "break fired".
     */
    fun armNextAlarm(context: Context, prefs: SharedPreferences) {
        val minutes = prefs.getInt(Prefs.INTERVAL_MINUTES, Prefs.DEFAULT_INTERVAL_MINUTES)
        armNextAlarm(context, prefs, minutes * 60_000L)
    }

    private fun armNextAlarm(context: Context, prefs: SharedPreferences, delayMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = SystemClock.elapsedRealtime() + delayMillis
        val pendingIntent = buildPendingIntent(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } else {
            am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
        }

        prefs.edit().putLong(Prefs.NEXT_BREAK_AT_ELAPSED, triggerAt).apply()
    }

    /** Milliseconds remaining until the next break, or null if not running. */
    fun millisRemaining(prefs: SharedPreferences): Long? {
        if (!isRunning(prefs)) return null
        val nextBreakAt = prefs.getLong(Prefs.NEXT_BREAK_AT_ELAPSED, 0L)
        return (nextBreakAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}

package com.eyerule.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = Prefs.from(context)
            if (AlarmScheduler.isRunning(prefs)) {
                val minutes = prefs.getInt(Prefs.INTERVAL_MINUTES, Prefs.DEFAULT_INTERVAL_MINUTES)
                AlarmScheduler.schedule(context, prefs, minutes)
            }
        }
    }
}

package com.eyerule.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("eyerule_prefs", Context.MODE_PRIVATE)
            if (AlarmScheduler.isRunning(prefs)) {
                val minutes = prefs.getInt("interval_minutes", 20)
                AlarmScheduler.schedule(context, prefs, minutes)
            }
        }
    }
}

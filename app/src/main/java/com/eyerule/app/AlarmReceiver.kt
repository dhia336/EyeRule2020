package com.eyerule.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Just show the overlay. The next break is armed later, from
        // BreakOverlayService, once the pause screen actually closes -
        // so the interval is measured break-end to break-end, not
        // alarm-fired to alarm-fired.
        context.startService(Intent(context, BreakOverlayService::class.java))
    }
}

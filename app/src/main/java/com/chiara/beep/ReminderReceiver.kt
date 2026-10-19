package com.chiara.beep

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val si = Intent(context, BeepService::class.java).apply {
            action = BeepService.ACTION_REMINDER
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(si)
        } else {
            context.startService(si)
        }
    }
}

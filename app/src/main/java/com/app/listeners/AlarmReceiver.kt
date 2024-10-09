package com.app.listeners

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver:BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // Handle alarm event here (e.g., trigger sound)
            Log.d("Alarm", "Alarm triggered!")
    }
}
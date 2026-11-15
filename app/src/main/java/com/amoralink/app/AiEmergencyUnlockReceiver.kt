package com.amoralink.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AiEmergencyUnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_EMERGENCY_UNLOCK) {
            AiControlManager.emergencyUnlock(context)
        }
    }

    companion object {
        const val ACTION_EMERGENCY_UNLOCK =
            "com.amoralink.app.ACTION_EMERGENCY_UNLOCK"
    }
}

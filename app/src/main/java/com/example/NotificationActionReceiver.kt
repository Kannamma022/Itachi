package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d("NotificationReceiver", "Received action: $action")
        val vm = VoiceViewModel.instance
        if (vm == null) {
            Log.w("NotificationReceiver", "VoiceViewModel instance is null!")
            return
        }
        when (action) {
            "com.example.ACTION_TOGGLE_MUTE" -> {
                vm.toggleMute()
            }
            "com.example.ACTION_DISCONNECT" -> {
                vm.leaveVoiceChannel()
            }
        }
    }
}

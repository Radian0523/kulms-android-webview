package com.radian0523.kulms_plus_for_android.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        val title = intent.getStringExtra("title") ?: return
        val body = intent.getStringExtra("body") ?: return
        NotificationHelper.showNotification(context, id, title, body)
    }
}

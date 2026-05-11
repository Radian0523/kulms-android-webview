package com.radian0523.kulms_plus_for_android.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject

/**
 * 端末再起動時に通知アラームを再登録する BroadcastReceiver。
 * AlarmManager のアラームは再起動で失われるため、
 * SharedPreferences に保存された課題データから再スケジュールする。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d("BootReceiver", "Device booted — rescheduling notifications")

        try {
            val prefs = context.getSharedPreferences("kulms_extension_storage", Context.MODE_PRIVATE)
            val raw = prefs.getString("kulms-extension-storage", null) ?: return
            val store = JSONObject(raw)

            val assignmentsData = store.optJSONObject("kulms-assignments") ?: return
            val assignments = assignmentsData.optJSONArray("assignments") ?: return
            val checked = store.optJSONObject("kulms-checked-assignments") ?: JSONObject()

            NotificationHelper.scheduleFromExtensionData(context, assignments, checked)
            Log.d("BootReceiver", "Notifications rescheduled after boot")
        } catch (e: Exception) {
            Log.e("BootReceiver", "Failed to reschedule notifications: ${e.message}")
        }
    }
}

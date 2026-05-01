package com.radian0523.kulms_plus_for_android.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.radian0523.kulms_plus_for_android.MainActivity
import com.radian0523.kulms_plus_for_android.R
import org.json.JSONArray
import org.json.JSONObject

object NotificationHelper {
    private const val TAG = "NotificationHelper"
    private const val CHANNEL_ID = "kulms_deadline"
    private const val PREFS_NAME = "kulms_settings"
    private const val OFFSETS_KEY = "notificationOffsets"
    private const val NEW_ASSIGNMENT_KEY = "newAssignmentNotification"
    private const val KNOWN_KEYS_KEY = "knownAssignmentKeys"
    private val DEFAULT_OFFSETS = setOf("1440", "60") // 24h, 1h (minutes)

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    // MARK: - Notification Offsets

    fun getNotificationOffsets(context: Context): List<Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(OFFSETS_KEY, null)
            ?: return DEFAULT_OFFSETS.map { it.toInt() }.sortedDescending()
        val offsets = set.mapNotNull { it.toIntOrNull() }
        return if (offsets.isEmpty()) DEFAULT_OFFSETS.map { it.toInt() }.sortedDescending()
        else offsets.sortedDescending()
    }

    fun saveNotificationOffsets(context: Context, offsets: List<Int>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(OFFSETS_KEY, offsets.map { it.toString() }.toSet()).apply()
    }

    // MARK: - New Assignment Notification

    fun getNewAssignmentNotification(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(NEW_ASSIGNMENT_KEY, true)
    }

    fun saveNewAssignmentNotification(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(NEW_ASSIGNMENT_KEY, enabled).apply()
    }

    private fun getKnownAssignmentKeys(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KNOWN_KEYS_KEY, null) ?: emptySet()
    }

    private fun saveKnownAssignmentKeys(context: Context, keys: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KNOWN_KEYS_KEY, keys).apply()
    }

    fun formatOffsetLabel(minutes: Int, context: Context? = null): String {
        return when {
            minutes >= 1440 && minutes % 1440 == 0 ->
                context?.getString(R.string.offset_days_before, minutes / 1440)
                    ?: "${minutes / 1440}日前"
            minutes >= 60 && minutes % 60 == 0 ->
                context?.getString(R.string.offset_hours_before, minutes / 60)
                    ?: "${minutes / 60}時間前"
            else ->
                context?.getString(R.string.offset_mins_before, minutes)
                    ?: "${minutes}分前"
        }
    }

    /**
     * 拡張機能から受け取った課題 JSON から通知をスケジュールする。
     *
     * @param context アプリケーションコンテキスト
     * @param assignments 課題の JSONArray（拡張機能の assignments.js が生成）
     * @param checkedState kulms-checked-assignments の JSONObject
     */
    fun scheduleFromExtensionData(
        context: Context,
        assignments: JSONArray,
        checkedState: JSONObject
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val manager = NotificationManagerCompat.from(context)
        manager.cancelAll()

        val now = System.currentTimeMillis()
        val offsets = getNotificationOffsets(context)
        val notifyNew = getNewAssignmentNotification(context)
        val knownKeys = getKnownAssignmentKeys(context)

        data class Candidate(val id: Int, val title: String, val body: String, val triggerAt: Long, val url: String)

        val candidates = mutableListOf<Candidate>()
        val currentKeys = mutableSetOf<String>()

        for (i in 0 until assignments.length()) {
            val obj = assignments.optJSONObject(i) ?: continue

            // 締切がない課題はスキップ
            val deadline = obj.optLong("deadline", 0L)
            if (deadline <= 0L) continue

            // 過去の締切はスキップ
            if (deadline <= now) continue

            // 提出済の課題はスキップ
            val status = obj.optString("status", "")
            if (status.isNotEmpty()) continue

            // compositeKey を生成
            val entityId = obj.optString("entityId", "")
            val courseId = obj.optString("courseId", "")
            val name = obj.optString("name", "")
            val compositeKey = if (entityId.isNotEmpty()) entityId else "$courseId:$name"

            // チェック済の課題はスキップ（値が truthy かつ "active" でなければスキップ）
            if (checkedState.has(compositeKey)) {
                val checkedValue = checkedState.opt(compositeKey)
                val isTruthy = when (checkedValue) {
                    null, false, 0, "" -> false
                    else -> true
                }
                if (isTruthy && checkedValue?.toString() != "active") continue
            }

            val courseName = obj.optString("courseName", "")
            val url = "https://lms.gakusei.kyoto-u.ac.jp/portal/site/$courseId"

            currentKeys.add(compositeKey)

            // 新着課題の即時通知
            if (notifyNew && knownKeys.isNotEmpty() && compositeKey !in knownKeys) {
                showNotification(
                    context,
                    id = "kulms-new-$compositeKey".hashCode(),
                    title = context.getString(R.string.notif_new_assignment_title),
                    body = context.getString(R.string.notif_new_assignment_body, name, courseName),
                    url = url
                )
            }

            for (offset in offsets) {
                val triggerAt = deadline - offset.toLong() * 60 * 1000
                if (triggerAt <= now) continue

                val label = formatOffsetLabel(offset, context).let {
                    if (it.endsWith("前")) it.dropLast(1) else it
                }
                val title = if (offset <= 60)
                    context.getString(R.string.notif_title_soon)
                else
                    context.getString(R.string.notif_title_approaching)
                val body = context.getString(R.string.notif_body, name, courseName, label)

                candidates.add(
                    Candidate(
                        id = "kulms-${offset}m-$compositeKey".hashCode(),
                        title = title,
                        body = body,
                        triggerAt = triggerAt,
                        url = url
                    )
                )
            }
        }

        // 既知の課題キーを更新
        if (currentKeys.isNotEmpty()) {
            saveKnownAssignmentKeys(context, currentKeys)
        }

        // 最も近い通知から順にスケジュール
        candidates.sortBy { it.triggerAt }

        Log.d(TAG, "scheduleFromExtensionData: scheduling ${candidates.size} notifications")

        for (candidate in candidates) {
            scheduleAlarm(context, candidate.id, candidate.title, candidate.body, candidate.triggerAt, candidate.url)
        }
    }

    private fun scheduleAlarm(
        context: Context,
        id: Int,
        title: String,
        body: String,
        triggerAt: Long,
        url: String = ""
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("id", id)
            putExtra("title", title)
            putExtra("body", body)
            putExtra("url", url)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent
            )
        } catch (_: SecurityException) {
            // Exact alarm permission not granted, fall back to inexact
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun showNotification(context: Context, id: Int, title: String, body: String, url: String = "") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (url.isNotEmpty()) putExtra("targetUrl", url)
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context, id, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }
}

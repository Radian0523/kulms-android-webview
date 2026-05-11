package com.radian0523.kulms_plus_for_android.notification

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.radian0523.kulms_plus_for_android.MainActivity
import com.radian0523.kulms_plus_for_android.R
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home Screen App Shortcuts に課題を登録する。
 * キャッシュされた課題データから締切が近い順に最大4件を動的ショートカットとして登録。
 */
object ShortcutHelper {
    private const val TAG = "ShortcutHelper"

    fun updateShortcuts(context: Context, assignments: JSONArray, checkedState: JSONObject) {
        val now = System.currentTimeMillis()
        val formatter = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

        data class Candidate(
            val compositeKey: String,
            val name: String,
            val courseName: String,
            val deadline: Long,
            val url: String
        )

        val candidates = mutableListOf<Candidate>()

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

            // チェック済の課題はスキップ
            if (checkedState.has(compositeKey)) {
                val checkedValue = checkedState.opt(compositeKey)
                val isTruthy = when (checkedValue) {
                    null, false, 0, "" -> false
                    else -> true
                }
                if (isTruthy && checkedValue?.toString() != "active") continue
            }

            val courseName = obj.optString("courseName", "")
            val url = obj.optString("url", "").ifEmpty {
                "https://lms.gakusei.kyoto-u.ac.jp/portal/site/$courseId"
            }

            candidates.add(Candidate(compositeKey, name, courseName, deadline, url))
        }

        // 締切が近い順にソートし、最大4件
        candidates.sortBy { it.deadline }
        val top = candidates.take(4)

        val shortcuts = top.map { candidate ->
            val deadlineStr = formatter.format(Date(candidate.deadline))
            val longLabel = context.getString(R.string.shortcut_deadline_format, candidate.courseName, deadlineStr)

            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("targetUrl", candidate.url)
            }

            val shortLabel = "$deadlineStr ${candidate.name}"
            val longLabelText = "$deadlineStr ${candidate.courseName} - ${candidate.name}"

            ShortcutInfoCompat.Builder(context, "assignment-${candidate.compositeKey}")
                .setShortLabel(shortLabel)
                .setLongLabel(longLabelText)
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(intent)
                .build()
        }

        ShortcutManagerCompat.removeAllDynamicShortcuts(context)
        ShortcutManagerCompat.addDynamicShortcuts(context, shortcuts)

        Log.d(TAG, "updateShortcuts: registered ${shortcuts.size} shortcuts")
    }
}

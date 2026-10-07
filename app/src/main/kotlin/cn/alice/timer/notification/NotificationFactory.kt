package cn.alice.timer.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import cn.alice.timer.MainActivity
import cn.alice.timer.R
import cn.alice.timer.core.Characters
import cn.alice.timer.core.DialoguePicker
import cn.alice.timer.core.Phase
import cn.alice.timer.data.AppData
import cn.alice.timer.monitor.Permissions

class NotificationFactory(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    fun createChannels() {
        manager.createNotificationChannel(NotificationChannel(MONITOR_CHANNEL, "后台监测", NotificationManager.IMPORTANCE_LOW).apply {
            description = "显示游戏监测与计时状态"; setShowBadge(false)
            if (Build.VERSION.SDK_INT >= 33) setBlockable(true)
        })
        manager.createNotificationChannel(NotificationChannel(REMINDER_CHANNEL, "角色摸头提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "角色发来的咖啡厅摸头提醒"; enableVibration(true)
        })
    }
    private fun openApp() = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun monitoring(data: AppData): Notification {
        val status = when (data.cycle.phase) {
            Phase.RUNNING -> "摸头倒计时进行中 · 重进游戏不会重置"
            Phase.NOTIFIED -> "本轮已到时间 · 等待下一次进入游戏"
            Phase.WAITING -> if (data.cycle.gameForeground) "游戏正在前台 · 离开后自动计时" else "等待进入并离开游戏"
        }
        return NotificationCompat.Builder(context, MONITOR_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("爱丽丝计时器 · ${data.gameLabel}")
            .setContentText(status).setContentIntent(openApp()).setOngoing(true).setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE).build()
    }
    fun updateMonitoring(data: AppData) { manager.notify(MONITOR_ID, monitoring(data)) }

    /** Posting with a stable ID makes a retried deadline replace its notification. */
    fun remind(data: AppData, preview: Boolean = false): String {
        val line = DialoguePicker.pick(data.lines, data.enabledCharacters) ?: return "到时间了，但没有启用的角色台词"
        val character = Characters.all.first { it.id == line.characterId }
        if (!Permissions.read(context).notifications) return "到时间了，但角色通知权限未开启"
        val avatar = CharacterAssets.load(context, character.id, data.avatarUris[character.id])
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("${character.name}：老师，摸头时间到了！")
            .setContentText(line.text).setStyle(NotificationCompat.BigTextStyle().bigText(line.text))
            .setLargeIcon(avatar).setColor(character.color.toInt()).setSubText(if (preview) "角色通知预览" else "爱丽丝计时器 · 咖啡厅来信")
            .setContentIntent(openApp()).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH).build()
        return runCatching {
            manager.notify(if (preview) PREVIEW_ID else REMINDER_ID, notification)
            "${character.name}：${line.text}"
        }.getOrElse { "到时间了，但系统未允许发送通知" }
    }
    companion object {
        const val MONITOR_CHANNEL = "monitor"
        const val REMINDER_CHANNEL = "character_reminders"
        const val MONITOR_ID = 10
        const val REMINDER_ID = 20
        const val PREVIEW_ID = 21
    }
}

package cn.alice.timer.monitor

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import androidx.core.content.ContextCompat
import cn.alice.timer.notification.NotificationFactory

data class PermissionStatus(
    val usage: Boolean = false,
    val notifications: Boolean = false,
    val exact: Boolean = false,
    val battery: Boolean = false,
    val monitorNotificationVisible: Boolean = false,
)

object Permissions {
    fun read(context: Context): PermissionStatus {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val usage = runCatching {
            val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
        val manager = context.getSystemService(NotificationManager::class.java)
        val runtimeGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val channelGranted = manager.getNotificationChannel(NotificationFactory.REMINDER_CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
        val monitorChannelGranted = manager.getNotificationChannel(NotificationFactory.MONITOR_CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
        val appNotificationsGranted = runtimeGranted && manager.areNotificationsEnabled()
        return PermissionStatus(
            usage = usage, notifications = appNotificationsGranted && channelGranted,
            exact = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            battery = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
            monitorNotificationVisible = appNotificationsGranted && monitorChannelGranted,
        )
    }
}

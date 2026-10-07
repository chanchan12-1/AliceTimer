package cn.alice.timer.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri
import cn.alice.timer.notification.NotificationFactory

object SystemSettings {
    private fun launch(context: Context, intents: List<Intent>): Boolean = intents.any { intent ->
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
    }
    private fun details(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
    fun usage(context: Context) = launch(context, listOf(
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, "package:${context.packageName}".toUri()), Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), details(context)))
    fun exact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        return launch(context, listOf(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()), details(context)))
    }
    fun notifications(context: Context) = launch(context, listOf(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName), details(context)))
    fun monitorNotification(context: Context) = launch(context, listOf(
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, NotificationFactory.MONITOR_CHANNEL),
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        details(context)))
    fun battery(context: Context) = launch(context, listOf(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), details(context)))

    fun autoStartInstructions(): String = when (Build.MANUFACTURER.lowercase()) {
        "huawei", "honor" -> "在应用启动管理中关闭自动管理，允许自动启动、关联启动和后台活动。"
        "oppo", "realme", "oneplus" -> "在应用管理中允许自启动与后台活动，并取消省电限制。"
        "vivo", "iqoo" -> "在 i 管家 → 应用管理中允许自启动，并允许后台高耗电运行。"
        else -> "如果系统提供「自启动」开关，请为爱丽丝计时器开启；原生 Android 通常没有单独开关。"
    }
    fun autoStart(context: Context) {
        val components = when (Build.MANUFACTURER.lowercase()) {
            "xiaomi", "redmi", "poco" -> listOf("com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity")
            "huawei", "honor" -> listOf("com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            "oppo", "realme", "oneplus" -> listOf(
                "com.oplus.safecenter" to "com.oplus.safecenter.startupapp.StartupAppListActivity",
                "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity")
            "vivo", "iqoo" -> listOf("com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
            else -> emptyList()
        }
        launch(context, components.map { (pkg, name) -> Intent().setComponent(ComponentName(pkg, name)) } + details(context))
        Toast.makeText(context, "请在系统页面检查自启动和后台活动权限", Toast.LENGTH_LONG).show()
    }
}

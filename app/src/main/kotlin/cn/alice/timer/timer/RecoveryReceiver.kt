package cn.alice.timer.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.alice.timer.graph
import cn.alice.timer.monitor.MonitorService
import kotlinx.coroutines.launch

class RecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        val pending = goAsync()
        context.graph.scope.launch {
            try {
                context.graph.sync(forceAlarm = true)
                MonitorService.tryStart(context)
            } finally { pending.finish() }
        }
    }
}

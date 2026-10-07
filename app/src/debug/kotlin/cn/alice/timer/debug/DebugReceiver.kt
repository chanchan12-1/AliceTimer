package cn.alice.timer.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.alice.timer.core.CycleState
import cn.alice.timer.core.ClockAnchor
import cn.alice.timer.data.GameVersions
import cn.alice.timer.graph

class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "cn.alice.timer.TEST") return
        val graph = context.graph
        val seconds = intent.getIntExtra("seconds", 20).coerceIn(5, 60)
        val server = intent.getStringExtra("server") ?: "cn"
        val index = when (server) { "jp" -> 1; "global" -> 2; else -> 0 }
        if (intent.getBooleanExtra("reset", false)) graph.store.update { it.copy(
            intervalMs = seconds * 1_000L, cycle = CycleState(), clock = ClockAnchor(),
            queryCursor = System.currentTimeMillis(), gamePackages = GameVersions.all[index].packages,
            gameLabel = GameVersions.all[index].title, lastReminderAt = 0, lastReminder = "", onboardingSeen = true,
            monitoringEnabled = true, autoStartConfirmed = true,
        ) }
    }
}

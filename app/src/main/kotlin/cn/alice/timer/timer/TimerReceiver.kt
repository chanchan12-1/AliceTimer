package cn.alice.timer.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.alice.timer.graph
import kotlinx.coroutines.launch

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_DUE) return
        val pending = goAsync()
        context.graph.scope.launch {
            try { context.graph.sync() } finally { pending.finish() }
        }
    }
}

package cn.alice.timer.timer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cn.alice.timer.graph
import cn.alice.timer.monitor.MonitorService
import cn.alice.timer.monitor.Permissions

class RecoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        val graph = applicationContext.graph
        graph.sync()
        // Background FGS starts are permitted on the battery-optimization allowlist.
        if (!graph.serviceRunning.value && Permissions.read(applicationContext).battery) MonitorService.tryStart(applicationContext)
        Result.success()
    }.getOrElse { Result.retry() }
}

package cn.alice.timer.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.work.*
import cn.alice.timer.core.CycleState
import cn.alice.timer.core.Phase
import java.util.concurrent.TimeUnit

class AlarmScheduler(private val context: Context) {
    private val alarm = context.getSystemService(AlarmManager::class.java)
    private var scheduledDue = Long.MIN_VALUE
    private var scheduledExact = false
    private fun pending() = PendingIntent.getBroadcast(context, 30,
        Intent(context, TimerReceiver::class.java).setAction(ACTION_DUE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun reconcile(state: CycleState, force: Boolean = false) {
        val exact = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
        val due = if (state.phase == Phase.RUNNING) state.dueAt else 0
        if (!force && due == scheduledDue && exact == scheduledExact) return
        if (due == 0L) {
            alarm.cancel(pending())
            WorkManager.getInstance(context).cancelUniqueWork(BACKUP_NAME)
        } else {
            val remaining = (due - System.currentTimeMillis()).coerceAtLeast(0)
            val elapsedDue = SystemClock.elapsedRealtime() + remaining
            // Monotonic alarm survives process death, screen-off and manual wall-clock edits.
            try {
                if (exact) alarm.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsedDue, pending())
                else alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsedDue, pending())
            } catch (_: SecurityException) {
                alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsedDue, pending())
            }
            val backup = OneTimeWorkRequestBuilder<RecoveryWorker>().setInitialDelay(remaining, TimeUnit.MILLISECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork(BACKUP_NAME, ExistingWorkPolicy.REPLACE, backup)
        }
        scheduledDue = due
        scheduledExact = exact
    }

    fun installRecovery() {
        val recovery = PeriodicWorkRequestBuilder<RecoveryWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("alice-recovery", ExistingPeriodicWorkPolicy.KEEP, recovery)
    }
    companion object {
        const val ACTION_DUE = "cn.alice.timer.DUE"
        private const val BACKUP_NAME = "alice-deadline"
    }
}

package cn.alice.timer

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import cn.alice.timer.core.*
import cn.alice.timer.data.*
import cn.alice.timer.monitor.*
import cn.alice.timer.notification.NotificationFactory
import cn.alice.timer.timer.AlarmScheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppGraph(val context: Context) {
    val store = AppStore(context)
    val notifications = NotificationFactory(context).also { it.createChannels() }
    private val reader = UsageEventReader(context)
    private val scheduler = AlarmScheduler(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val permissionState = MutableStateFlow(Permissions.read(context))
    val permissions = permissionState.asStateFlow()
    val serviceRunning = MutableStateFlow(false)
    val monitorError = MutableStateFlow("")

    fun refreshPermissions() { permissionState.value = Permissions.read(context) }
    fun initialize() = scope.launch {
        if (store.value.onboardingSeen) {
            scheduler.installRecovery()
            sync(forceAlarm = true)
        }
    }

    /** Serialized across foreground polling, alarms, reboot recovery and WorkManager. */
    fun sync(extra: Observation? = null, forceAlarm: Boolean = false) = synchronized(lock) {
        val wall = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        val old = store.value
        if (!old.onboardingSeen) return@synchronized
        val shift = old.clock.wallShift(wall, elapsed, boot)
        var cycle = old.cycle.shiftWallClock(shift)
        val rebooted = old.clock.wall > 0 && !old.clock.isSameBoot(elapsed, boot)
        var cursor = when {
            old.queryCursor == 0L -> wall
            shift != 0L || old.queryCursor > wall -> (wall - 5_000).coerceAtLeast(0)
            else -> old.queryCursor
        }
        val events = mutableListOf<Observation>()
        if (rebooted && cycle.gameForeground) events += Observation(ObservationKind.LEFT, (wall - elapsed).coerceAtLeast(1))
        refreshPermissions()
        if (old.monitoringEnabled && permissionState.value.usage) {
            val history = runCatching { reader.read(cursor.coerceAtLeast(wall - 7 * 24 * 60 * 60 * 1000L), wall, old.gamePackages) }
                .onFailure { monitorError.value = "监测暂不可用：${it.javaClass.simpleName}" }.getOrNull()
            if (history != null) { events += history; cursor = wall; monitorError.value = "" }
        } else cursor = wall
        if (extra != null && old.monitoringEnabled) events += extra
        val result = CycleEngine.advance(cycle, events, wall, old.intervalMs)
        var lastAt = if (old.lastReminderAt == 0L) 0 else old.lastReminderAt + shift
        var lastText = old.lastReminder
        for (deadline in result.reminders) {
            if (deadline != lastAt) {
                lastText = notifications.remind(store.value)
                lastAt = deadline
            }
        }
        val next = store.update { it.copy(cycle = result.state, queryCursor = cursor,
            clock = ClockAnchor(wall, elapsed, boot), lastReminderAt = lastAt, lastReminder = lastText) }
        scheduler.reconcile(next.cycle, forceAlarm || shift != 0L || rebooted)
    }

    fun completeOnboarding() = scope.launch {
        synchronized(lock) {
            store.update { it.copy(onboardingSeen = true, queryCursor = if (it.queryCursor == 0L) System.currentTimeMillis() else it.queryCursor) }
            scheduler.installRecovery()
        }
        sync(forceAlarm = true)
    }

    fun setMonitoring(enabled: Boolean) = scope.launch {
        synchronized(lock) {
            store.update { it.copy(monitoringEnabled = enabled, queryCursor = System.currentTimeMillis(),
                cycle = it.cycle.copy(gameForeground = false, pendingExitAt = 0)) }
        }
        if (enabled) MonitorService.tryStart(context) else MonitorService.stop(context)
    }

    suspend fun startTimerNow() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            sync()
            val now = System.currentTimeMillis()
            val data = store.value
            saveManualCycle(CycleEngine.startNow(data.cycle, now, data.intervalMs), now)
        }
    }

    suspend fun changeRemainingTime(remainingMs: Long): Boolean = withContext(Dispatchers.IO) {
        synchronized(lock) {
            sync()
            val now = System.currentTimeMillis()
            val data = store.value
            if (data.cycle.phase != Phase.RUNNING || now >= data.cycle.dueAt || remainingMs <= 0 || remainingMs > data.intervalMs) {
                return@synchronized false
            }
            saveManualCycle(CycleEngine.changeRemaining(data.cycle, now, remainingMs, data.intervalMs), now)
            true
        }
    }

    private fun saveManualCycle(cycle: CycleState, now: Long) {
        val anchor = ClockAnchor(now, SystemClock.elapsedRealtime(), Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1))
        val next = store.update { it.copy(cycle = cycle, clock = anchor) }
        scheduler.reconcile(next.cycle, force = true)
        scheduler.installRecovery()
    }

    fun selectGame(title: String, packages: Set<String>): Boolean = synchronized(lock) {
        if (store.value.cycle.phase == Phase.RUNNING) return@synchronized false
        store.update { it.copy(gameLabel = title, gamePackages = packages, cycle = CycleState(), queryCursor = System.currentTimeMillis()) }
        true
    }
}

val Context.graph: AppGraph get() = (applicationContext as AliceApplication).graph

package cn.alice.timer.core

enum class Phase { WAITING, RUNNING, NOTIFIED }
enum class ObservationKind { OPENED, LEFT }
data class Observation(val kind: ObservationKind, val at: Long)

data class CycleState(
    val phase: Phase = Phase.WAITING,
    val gameForeground: Boolean = false,
    val pendingExitAt: Long = 0,
    val startedAt: Long = 0,
    val dueAt: Long = 0,
    val notifiedAt: Long = 0,
) {
    fun shiftWallClock(delta: Long): CycleState = copy(
        pendingExitAt = pendingExitAt.shift(delta),
        startedAt = startedAt.shift(delta),
        dueAt = dueAt.shift(delta),
        notifiedAt = notifiedAt.shift(delta),
    )
    private fun Long.shift(delta: Long) = if (this == 0L) 0L else this + delta
}

data class AdvanceResult(val state: CycleState, val reminders: List<Long>)

/** Pure, replayable reducer. Deadlines are persisted timestamps, never UI countdown ticks. */
object CycleEngine {
    const val EXIT_GRACE_MS = 1_500L

    fun startNow(initial: CycleState, now: Long, intervalMs: Long): CycleState {
        require(now > 0 && intervalMs > 0)
        return CycleState(phase = Phase.RUNNING, gameForeground = initial.gameForeground,
            startedAt = now, dueAt = now + intervalMs)
    }

    fun changeRemaining(initial: CycleState, now: Long, remainingMs: Long, maxIntervalMs: Long): CycleState {
        require(initial.phase == Phase.RUNNING && now < initial.dueAt)
        require(remainingMs > 0 && remainingMs <= maxIntervalMs)
        return initial.copy(dueAt = now + remainingMs, pendingExitAt = 0, notifiedAt = 0)
    }

    fun advance(initial: CycleState, events: List<Observation>, now: Long, intervalMs: Long): AdvanceResult {
        require(intervalMs > 0)
        var state = initial
        val reminders = mutableListOf<Long>()

        fun tick(at: Long) {
            if (state.pendingExitAt > 0 && at >= state.pendingExitAt + EXIT_GRACE_MS) {
                val exit = state.pendingExitAt
                state = if (state.phase == Phase.WAITING) {
                    state.copy(phase = Phase.RUNNING, pendingExitAt = 0, startedAt = exit, dueAt = exit + intervalMs)
                } else state.copy(pendingExitAt = 0)
            }
            if (state.phase == Phase.RUNNING && at >= state.dueAt) {
                reminders += state.dueAt
                state = state.copy(phase = Phase.NOTIFIED, notifiedAt = state.dueAt)
            }
        }

        for (event in events.sortedBy { it.at }) {
            if (event.at > now) continue
            tick(event.at)
            state = when (event.kind) {
                ObservationKind.OPENED -> {
                    // A duplicate RESUMED or an internal activity switch cannot complete a cycle.
                    val entering = !state.gameForeground && state.pendingExitAt == 0L
                    if (entering && state.phase == Phase.NOTIFIED && event.at > state.notifiedAt) {
                        CycleState(gameForeground = true)
                    } else state.copy(gameForeground = true, pendingExitAt = 0)
                }
                ObservationKind.LEFT -> {
                    if (state.gameForeground) state.copy(gameForeground = false, pendingExitAt = event.at)
                    else state
                }
            }
        }
        tick(now)
        return AdvanceResult(state, reminders)
    }
}

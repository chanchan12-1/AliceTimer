package cn.alice.timer.core

import org.junit.Assert.*
import org.junit.Test

class ManualTimerTest {
    private val now = 1_000_000L
    private val interval = 3 * 60 * 60 * 1000L

    @Test fun startsOrRestartsAFullIntervalFromEveryPhase() {
        for (phase in Phase.entries) {
            val old = CycleState(phase, gameForeground = true, pendingExitAt = now - 5_000,
                startedAt = now - interval, dueAt = now + 500, notifiedAt = now - 1)
            val next = CycleEngine.startNow(old, now, interval)
            assertEquals(Phase.RUNNING, next.phase)
            assertEquals(now, next.startedAt)
            assertEquals(now + interval, next.dueAt)
            assertEquals(0L, next.pendingExitAt)
            assertEquals(0L, next.notifiedAt)
            assertTrue(next.gameForeground)
        }
    }

    @Test fun automaticExitAndReentryCannotOverwriteManualDeadline() {
        val manual = CycleEngine.startNow(CycleState(), now, interval)
        val events = listOf(Observation(ObservationKind.OPENED, now + 1_000), Observation(ObservationKind.LEFT, now + 2_000))
        assertEquals(manual.dueAt, CycleEngine.advance(manual, events, now + 5_000, interval).state.dueAt)
    }

    @Test fun shorteningTheTimerRemindsAtTheEditedDeadlineExactlyOnce() {
        val original = CycleEngine.startNow(CycleState(), now, interval)
        val edited = CycleEngine.changeRemaining(original, now + 60_000, 60_000, interval)
        assertEquals(original.startedAt, edited.startedAt)
        val expired = CycleEngine.advance(edited, emptyList(), edited.dueAt, interval)
        assertEquals(listOf(edited.dueAt), expired.reminders)
        assertTrue(CycleEngine.advance(expired.state, emptyList(), original.dueAt, interval).reminders.isEmpty())
    }

    @Test fun extendingTimeIgnoresAnAlreadyScheduledOldAlarm() {
        val original = CycleEngine.startNow(CycleState(), now, interval)
        val editedAt = original.dueAt - 30_000
        val edited = CycleEngine.changeRemaining(original, editedAt, interval, interval)
        val oldAlarm = CycleEngine.advance(edited, emptyList(), original.dueAt, interval)
        assertEquals(Phase.RUNNING, oldAlarm.state.phase)
        assertTrue(oldAlarm.reminders.isEmpty())
        assertEquals(editedAt + interval, edited.dueAt)
    }

    @Test fun clockChangesPreserveEditedRemainingTime() {
        val edited = CycleEngine.changeRemaining(CycleEngine.startNow(CycleState(), now, interval), now + 10_000, 120_000, interval)
        assertEquals(120_000, edited.shiftWallClock(3_600_000).dueAt - (now + 10_000 + 3_600_000))
    }

    @Test fun onlyAnActiveTimerWithPositiveTimeAtMostTheConfiguredIntervalCanBeEdited() {
        val active = CycleEngine.startNow(CycleState(), now, interval)
        for (remaining in listOf(-1L, 0L, interval + 1)) {
            assertThrows(IllegalArgumentException::class.java) { CycleEngine.changeRemaining(active, now, remaining, interval) }
        }
        for (phase in listOf(Phase.WAITING, Phase.NOTIFIED)) {
            assertThrows(IllegalArgumentException::class.java) { CycleEngine.changeRemaining(active.copy(phase = phase), now, 60_000, interval) }
        }
        assertThrows(IllegalArgumentException::class.java) { CycleEngine.changeRemaining(active, active.dueAt, 60_000, interval) }
        assertEquals(now + interval, CycleEngine.changeRemaining(active, now, interval, interval).dueAt)
    }
}

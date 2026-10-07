package cn.alice.timer.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class CycleEngineTest {
    private val start = 1_000_000L
    private val interval = 3 * 60 * 60 * 1000L
    private fun open(at: Long) = Observation(ObservationKind.OPENED, at)
    private fun leave(at: Long) = Observation(ObservationKind.LEFT, at)
    private fun running(): CycleState = CycleEngine.advance(CycleState(), listOf(open(start), leave(start + 1_000)), start + 3_000, interval).state

    @Test fun startsFromActualExitTimestampRatherThanPollingTime() {
        val state = running()
        assertEquals(Phase.RUNNING, state.phase)
        assertEquals(start + 1_000, state.startedAt)
        assertEquals(start + 1_000 + interval, state.dueAt)
    }
    @Test fun backgroundWithoutObservedGameDoesNotStartTimer() {
        assertEquals(Phase.WAITING, CycleEngine.advance(CycleState(), listOf(leave(start)), start + 5_000, interval).state.phase)
    }
    @Test fun reenteringBeforeExpiryAndLeavingAgainNeverResetTimer() {
        val original = running()
        val state = CycleEngine.advance(original, listOf(open(start + 2 * 60 * 60 * 1000), leave(start + 2 * 60 * 60 * 1000 + 5_000)), start + 2 * 60 * 60 * 1000 + 9_000, interval).state
        assertEquals(original.startedAt, state.startedAt); assertEquals(original.dueAt, state.dueAt)
    }
    @Test fun expiryIsEmittedOnceEvenWhenAlarmAndWorkerBothRun() {
        val first = CycleEngine.advance(running(), emptyList(), running().dueAt, interval)
        assertEquals(listOf(running().dueAt), first.reminders)
        assertEquals(Phase.NOTIFIED, first.state.phase)
        assertTrue(CycleEngine.advance(first.state, emptyList(), first.state.dueAt + 10_000, interval).reminders.isEmpty())
    }
    @Test fun onlyEntryAfterReminderRearmsNextExit() {
        val original = running()
        val notified = CycleEngine.advance(original, emptyList(), original.dueAt, interval).state
        val state = CycleEngine.advance(notified, listOf(open(original.dueAt + 5_000), leave(original.dueAt + 10_000)), original.dueAt + 12_000, interval).state
        assertEquals(Phase.RUNNING, state.phase); assertEquals(original.dueAt + 10_000, state.startedAt)
    }
    @Test fun expiryWhileGameOpenWaitsForAFutureRealEntry() {
        val original = running()
        val reopened = CycleEngine.advance(original, listOf(open(original.dueAt - 5_000)), original.dueAt, interval).state
        val left = CycleEngine.advance(reopened, listOf(leave(original.dueAt + 5_000)), original.dueAt + 8_000, interval).state
        assertEquals(Phase.NOTIFIED, left.phase)
        assertEquals(Phase.WAITING, CycleEngine.advance(left, listOf(open(original.dueAt + 10_000)), original.dueAt + 10_000, interval).state.phase)
    }
    @Test fun internalActivitySwitchDoesNotStartOrRearmCycle() {
        val switched = CycleEngine.advance(CycleState(), listOf(open(start), leave(start + 1_000), open(start + 1_100)), start + 10_000, interval).state
        assertEquals(Phase.WAITING, switched.phase); assertTrue(switched.gameForeground)
        val notified = CycleState(Phase.NOTIFIED, gameForeground = true, notifiedAt = start)
        val second = CycleEngine.advance(notified, listOf(leave(start + 2_000), open(start + 2_100)), start + 5_000, interval).state
        assertEquals(Phase.NOTIFIED, second.phase)
    }
    @Test fun replayAfterProcessDeathRespectsHistoricalDeadlineAndEntries() {
        val result = CycleEngine.advance(CycleState(), listOf(open(start), leave(start + 1_000), open(start + interval + 5_000), leave(start + interval + 9_000)), start + interval + 12_000, interval)
        assertEquals(listOf(start + 1_000 + interval), result.reminders)
        assertEquals(start + interval + 9_000, result.state.startedAt)
    }
    @Test fun duplicateLeavesDoNotShiftDeadline() {
        val state = CycleEngine.advance(CycleState(), listOf(open(start), leave(start + 1_000), leave(start + 2_000), leave(start + 4_000)), start + 6_000, interval).state
        assertEquals(start + 1_000, state.startedAt)
    }
    @Test fun intervalChangesOnlyAffectNextCycle() {
        val original = running()
        assertEquals(original.dueAt, CycleEngine.advance(original, emptyList(), start + 30_000, 60_000).state.dueAt)
    }
    @Test fun manualClockEditPreservesRemainingDuration() {
        val anchor = ClockAnchor(start, 5_000, 7)
        val shift = anchor.wallShift(start + 60_000 + 3_600_000, 65_000, 7)
        val original = running()
        val shifted = original.shiftWallClock(shift)
        assertEquals(3_600_000, shift)
        assertEquals(original.dueAt - (start + 60_000), shifted.dueAt - (start + 60_000 + 3_600_000))
    }
    @Test fun rebootUsesSavedWallDeadlineInsteadOfOldElapsedCounter() {
        assertEquals(0L, ClockAnchor(start, 5_000, 7).wallShift(start + 20_000, 300, 8))
        assertFalse(ClockAnchor(start, 5_000, 7).isSameBoot(300, 8))
    }
    @Test fun futureEventsAreNotConsumed() {
        assertEquals(CycleState(), CycleEngine.advance(CycleState(), listOf(open(start + 1)), start, interval).state)
    }
    @Test fun pickerExcludesDisabledCharactersLinesAndBlankText() {
        val lines = listOf(Dialogue("1", "yuzu", "hi", false), Dialogue("2", "yuzu", " "), Dialogue("3", "aris", "yes"), Dialogue("4", "midori", "no"))
        assertEquals("3", DialoguePicker.pick(lines, setOf("yuzu", "aris"), Random(1))!!.id)
        assertNull(DialoguePicker.pick(lines, setOf("yuzu")))
    }
    @Test fun randomCharacterIsNotWeightedByNumberOfLines() {
        val lines = (1..50).map { Dialogue("a$it", "yuzu", "x") } + Dialogue("b", "aris", "y")
        val random = Random(17)
        val yuzu = (1..2_000).count { DialoguePicker.pick(lines, setOf("yuzu", "aris"), random)!!.characterId == "yuzu" }
        assertTrue("Uniform character count: $yuzu", yuzu in 850..1_150)
    }
}

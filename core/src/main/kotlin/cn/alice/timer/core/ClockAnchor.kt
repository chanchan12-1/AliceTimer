package cn.alice.timer.core

/** elapsedRealtime includes sleep and is unaffected by wall-clock edits. */
data class ClockAnchor(val wall: Long = 0, val elapsed: Long = 0, val boot: Int = -1) {
    fun isSameBoot(currentElapsed: Long, currentBoot: Int): Boolean =
        wall > 0 && currentElapsed >= elapsed && boot == currentBoot

    fun wallShift(currentWall: Long, currentElapsed: Long, currentBoot: Int): Long {
        if (!isSameBoot(currentElapsed, currentBoot)) return 0
        val delta = currentWall - (wall + currentElapsed - elapsed)
        return if (kotlin.math.abs(delta) > 2_000) delta else 0
    }
}

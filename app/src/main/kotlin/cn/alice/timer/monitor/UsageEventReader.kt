package cn.alice.timer.monitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import cn.alice.timer.core.Observation
import cn.alice.timer.core.ObservationKind

class UsageEventReader(context: Context) {
    private val manager = context.getSystemService(UsageStatsManager::class.java)

    @Suppress("DEPRECATION")
    fun read(from: Long, until: Long, games: Set<String>): List<Observation>? {
        // On Android 11+, queryEvents can return null before the first unlock after reboot.
        val events = manager.queryEvents(from, until) ?: return null
        val result = mutableListOf<Observation>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> result += Observation(
                    if (event.packageName in games) ObservationKind.OPENED else ObservationKind.LEFT, event.timeStamp,
                )
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (event.packageName in games) {
                    result += Observation(ObservationKind.LEFT, event.timeStamp)
                }
                else -> if (Build.VERSION.SDK_INT >= 28 &&
                    (event.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE || event.eventType == UsageEvents.Event.KEYGUARD_SHOWN)) {
                    result += Observation(ObservationKind.LEFT, event.timeStamp)
                }
            }
        }
        return result
    }
}

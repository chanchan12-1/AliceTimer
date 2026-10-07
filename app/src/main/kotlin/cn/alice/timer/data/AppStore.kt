package cn.alice.timer.data

import android.content.Context
import cn.alice.timer.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** One process, one store. commit() persists the transition before an alarm is scheduled. */
class AppStore(context: Context) {
    private val prefs = context.getSharedPreferences("alice_timer_v1", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val data = mutable.asStateFlow()
    val value get() = mutable.value

    @Synchronized fun update(transform: (AppData) -> AppData): AppData {
        val next = transform(mutable.value)
        if (next != mutable.value) {
            check(prefs.edit().putString("data", encode(next).toString()).commit()) { "无法保存计时状态，请检查存储空间" }
            mutable.value = next
        }
        return next
    }

    private fun read(): AppData = runCatching {
        val json = prefs.getString("data", null) ?: return AppData()
        val j = JSONObject(json)
        val defaults = AppData()
        val c = j.optJSONObject("cycle") ?: JSONObject()
        val clock = j.optJSONObject("clock") ?: JSONObject()
        val avatars = j.optJSONObject("avatars") ?: JSONObject()
        val saved = defaults.copy(
            catalogVersion = j.optInt("catalogVersion", 1),
            intervalMs = j.optLong("interval", defaults.intervalMs).coerceIn(1_000L, 7 * 24 * 60 * 60 * 1000L),
            gamePackages = j.optJSONArray("packages")?.strings()?.toSet()?.takeIf { it.isNotEmpty() } ?: defaults.gamePackages,
            gameLabel = j.optString("gameLabel", defaults.gameLabel),
            monitoringEnabled = j.optBoolean("monitoring", true),
            onboardingSeen = j.optBoolean("onboarding"),
            autoStartConfirmed = j.optBoolean("autoStart"),
            enabledCharacters = j.optJSONArray("characters")?.strings()?.toSet() ?: defaults.enabledCharacters,
            lines = j.optJSONArray("lines")?.let { array -> (0 until array.length()).map { i ->
                val line = array.getJSONObject(i)
                Dialogue(line.getString("id"), line.getString("character"), line.getString("text"), line.optBoolean("enabled", true))
            } } ?: defaults.lines,
            avatarUris = avatars.keys().asSequence().associateWith { avatars.getString(it) },
            cycle = CycleState(
                phase = runCatching { Phase.valueOf(c.optString("phase", "WAITING")) }.getOrDefault(Phase.WAITING),
                gameForeground = c.optBoolean("foreground"), pendingExitAt = c.optLong("pending"),
                startedAt = c.optLong("started"), dueAt = c.optLong("due"), notifiedAt = c.optLong("notified"),
            ),
            queryCursor = j.optLong("cursor"),
            clock = ClockAnchor(clock.optLong("wall"), clock.optLong("elapsed"), clock.optInt("boot", -1)),
            lastReminderAt = j.optLong("lastReminderAt"), lastReminder = j.optString("lastReminder"),
        )
        if (saved.catalogVersion < 2) {
            val catalog = CatalogMigration.upgrade(saved.lines, saved.enabledCharacters, defaults.lines)
            val upgraded = saved.copy(catalogVersion = 2, lines = catalog.lines, enabledCharacters = catalog.enabledCharacters)
            check(prefs.edit().putString("data", encode(upgraded).toString()).commit()) { "无法保存角色更新" }
            upgraded
        } else saved
    }.getOrElse { AppData() }

    fun encode(data: AppData): JSONObject = JSONObject().apply {
        put("catalogVersion", data.catalogVersion)
        put("interval", data.intervalMs); put("packages", JSONArray(data.gamePackages.toList())); put("gameLabel", data.gameLabel)
        put("monitoring", data.monitoringEnabled); put("onboarding", data.onboardingSeen); put("autoStart", data.autoStartConfirmed)
        put("characters", JSONArray(data.enabledCharacters.toList()))
        put("lines", JSONArray().apply { data.lines.forEach { line -> put(JSONObject().apply {
            put("id", line.id); put("character", line.characterId); put("text", line.text); put("enabled", line.enabled)
        }) } })
        put("avatars", JSONObject(data.avatarUris))
        put("cycle", JSONObject().apply {
            put("phase", data.cycle.phase.name); put("foreground", data.cycle.gameForeground)
            put("pending", data.cycle.pendingExitAt); put("started", data.cycle.startedAt)
            put("due", data.cycle.dueAt); put("notified", data.cycle.notifiedAt)
        })
        put("cursor", data.queryCursor)
        put("clock", JSONObject().apply { put("wall", data.clock.wall); put("elapsed", data.clock.elapsed); put("boot", data.clock.boot) })
        put("lastReminderAt", data.lastReminderAt); put("lastReminder", data.lastReminder)
    }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}

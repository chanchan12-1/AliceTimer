package cn.alice.timer.data

import cn.alice.timer.core.ClockAnchor
import cn.alice.timer.core.CycleState
import cn.alice.timer.core.Dialogue
import cn.alice.timer.core.Characters

data class GameVersion(val title: String, val packages: Set<String>)
object GameVersions {
    val all = listOf(
        GameVersion("国服 · 蔚蓝档案（含 B 服）", setOf("com.RoamingStar.BlueArchive", "com.RoamingStar.BlueArchive.bilibili")),
        GameVersion("日服 · ブルーアーカイブ", setOf("com.YostarJP.BlueArchive")),
        GameVersion("国际服 · Blue Archive / 蔚藍檔案", setOf("com.nexon.bluearchive", "com.nexon.bluearchiveteen")),
    )
}
data class AppData(
    val catalogVersion: Int = 2,
    val intervalMs: Long = 3 * 60 * 60 * 1000L,
    val gamePackages: Set<String> = GameVersions.all.first().packages,
    val gameLabel: String = GameVersions.all.first().title,
    val monitoringEnabled: Boolean = true,
    val onboardingSeen: Boolean = false,
    val autoStartConfirmed: Boolean = false,
    val enabledCharacters: Set<String> = Characters.all.map { it.id }.toSet(),
    val lines: List<Dialogue> = DefaultDialogues.all,
    val avatarUris: Map<String, String> = emptyMap(),
    val cycle: CycleState = CycleState(),
    val queryCursor: Long = 0,
    val clock: ClockAnchor = ClockAnchor(),
    val lastReminderAt: Long = 0,
    val lastReminder: String = "",
)

object DefaultDialogues {
    private val texts = linkedMapOf(
        "aris" to listOf(
            "邦邦卡邦！咖啡厅的等待任务完成了！\n老师，请和爱丽丝一起领取摸头奖励吧！",
            "老师，爱丽丝收到新的任务提示：回到咖啡厅，和伙伴们会合！\n勇者小队已经准备好了！",
            "爱丽丝的提醒魔法发动成功！\n老师，下一段冒险开始前，先来咖啡厅摸摸大家的头吧！",
        ),
        "momoi" to listOf(
            "老师老师！摸头时间到了！\n这次可不能错过，快来咖啡厅和我们会合！",
            "我想到新剧情了！主角老师回到咖啡厅，给大家摸摸头，然后全员干劲满满！\n怎么样，快来演这一段吧！",
            "老师，暂停键按得够久啦！\n来咖啡厅摸摸头，再陪我挑战下一关嘛！",
        ),
        "midori" to listOf(
            "老师，摸头时间到了。\n我画画时顺便记着时间呢……来咖啡厅休息一下，好吗？",
            "咖啡厅的光线刚刚好，我想画下大家开心的样子。\n老师也来吧，大家都很期待你。",
            "姐姐已经迫不及待了……不过老师不用着急。\n有空就来咖啡厅摸摸我们的头吧。",
        ),
        "yuzu" to listOf(
            "老、老师……摸头时间到了。\n我会在咖啡厅等你，这次……会从柜子里出来的。",
            "老师，游戏可以先存个档……\n要不要来咖啡厅休息一下？我也想和你一起待一会儿。",
            "那个……我练习过很多次提醒老师的话了。\n咖啡厅的摸头时间到了，请、请回来看看我们……！",
        ),
    )
    val all = texts.flatMap { (id, lines) -> lines.mapIndexed { i, text -> Dialogue("$id-$i", id, text) } }
}

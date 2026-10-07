package cn.alice.timer.core

data class MigratedCatalog(val lines: List<Dialogue>, val enabledCharacters: Set<String>)

/** Upgrade shipped text without replacing user edits, deletions or enable/disable choices. */
object CatalogMigration {
    val legacyDefaults: List<Dialogue> = linkedMapOf(
        "yuzu" to listOf(
            "老师，咖啡厅好像又恢复活力了……\n回来看看大家吧 (｡･ω･｡)",
            "老师！新的游戏时间到了！\n大家正在等待你的指令呢！",
            "老师，休息时间结束啦！\n今天也一起完成新的冒险吧！",
        ),
        "hibiki" to listOf(
            "老师，咖啡厅已经准备好了哦！\n记得回来看看大家。",
            "老师，新的挑战时间开始啦！\n不要错过重要时刻。",
            "老师，大家都在等你回来呢 (≧▽≦)",
        ),
        "momoi" to listOf(
            "老师！\n摸头时间到了！\n快回来啦！",
            "老师，咖啡厅今天也很热闹哦！",
            "老师，再不回来我要启动特别计划了！",
        ),
        "midori" to listOf(
            "老师，新的游戏事件刷新啦！\n快回来看看！",
            "老师，今天也要继续努力升级哦！",
            "老师，咖啡厅需要你的支持呢！",
        ),
    ).flatMap { (character, texts) -> texts.mapIndexed { index, text -> Dialogue("$character-$index", character, text) } }

    fun upgrade(lines: List<Dialogue>, enabled: Set<String>, defaults: List<Dialogue>): MigratedCatalog {
        val legacy = legacyDefaults.associateBy { it.id }
        val replacements = defaults.associateBy { it.id }
        val migrated = lines.map { line ->
            val character = if (line.characterId == "hibiki") "aris" else line.characterId
            val id = if (line.characterId == "hibiki" && line.id in legacy) line.id.replaceFirst("hibiki-", "aris-") else line.id
            val original = legacy[line.id]
            val replacement = replacements[id]
            if (original != null && original.characterId == line.characterId && original.text == line.text && replacement != null) {
                replacement.copy(enabled = line.enabled)
            } else line.copy(id = id, characterId = character)
        }
        return MigratedCatalog(migrated, enabled.map { if (it == "hibiki") "aris" else it }.toSet())
    }
}

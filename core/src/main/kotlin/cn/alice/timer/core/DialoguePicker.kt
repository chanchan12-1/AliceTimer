package cn.alice.timer.core

import kotlin.random.Random

data class Dialogue(val id: String, val characterId: String, val text: String, val enabled: Boolean = true)
data class Character(val id: String, val name: String, val fullName: String, val englishName: String, val color: Long)

object Characters {
    val all = listOf(
        Character("aris", "爱丽丝", "天童爱丽丝", "Aris", 0xFF8EABCE),
        Character("momoi", "桃", "才羽桃", "Momoi", 0xFFE9A1B6),
        Character("midori", "绿", "才羽绿", "Midori", 0xFF92BBA2),
        Character("yuzu", "柚子", "花冈柚子", "Yuzu", 0xFFEFA877),
    )
}

object DialoguePicker {
    /** Choose an eligible character uniformly first, then one of their enabled lines. */
    fun pick(lines: List<Dialogue>, enabledCharacters: Set<String>, random: Random = Random.Default): Dialogue? {
        val knownCharacters = Characters.all.map { it.id }.toSet()
        val groups = lines.filter { it.enabled && it.text.isNotBlank() && it.characterId in enabledCharacters && it.characterId in knownCharacters }
            .groupBy { it.characterId }
        if (groups.isEmpty()) return null
        val character = groups.keys.toList().random(random)
        return groups.getValue(character).random(random)
    }
}

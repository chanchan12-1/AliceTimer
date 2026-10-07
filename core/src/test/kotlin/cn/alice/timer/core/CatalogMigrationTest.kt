package cn.alice.timer.core

import org.junit.Assert.*
import org.junit.Test

class CatalogMigrationTest {
    private val defaults = listOf(
        Dialogue("aris-0", "aris", "New Aris reminder"),
        Dialogue("aris-1", "aris", "Second Aris reminder"),
        Dialogue("momoi-0", "momoi", "New Momoi reminder"),
        Dialogue("yuzu-0", "yuzu", "New Yuzu reminder"),
    )
    private fun old(id: String) = CatalogMigration.legacyDefaults.first { it.id == id }

    @Test fun shippedTextAndCharacterSelectionAreUpgraded() {
        val result = CatalogMigration.upgrade(listOf(old("hibiki-0"), old("momoi-0"), old("yuzu-0")), setOf("hibiki", "momoi", "yuzu"), defaults)
        assertEquals(listOf(defaults[0], defaults[2], defaults[3]), result.lines)
        assertEquals(setOf("aris", "momoi", "yuzu"), result.enabledCharacters)
    }

    @Test fun editedAndCustomTextSurviveCharacterReplacement() {
        val result = CatalogMigration.upgrade(listOf(old("hibiki-0").copy(text = "My edited reminder"), Dialogue("user-uuid", "hibiki", "My custom reminder")), setOf("hibiki"), defaults)
        assertEquals(listOf(Dialogue("aris-0", "aris", "My edited reminder"), Dialogue("user-uuid", "aris", "My custom reminder")), result.lines)
    }

    @Test fun disabledChoicesAndDeletedLinesStayThatWay() {
        val result = CatalogMigration.upgrade(listOf(old("hibiki-0").copy(enabled = false)), emptySet(), defaults)
        assertEquals(listOf(defaults[0].copy(enabled = false)), result.lines)
        assertTrue(result.enabledCharacters.isEmpty())
        assertFalse(result.lines.any { it.id == "aris-1" })
    }

    @Test fun upgradingAnAlreadyUpgradedCatalogDoesNotEraseLaterEdits() {
        val first = CatalogMigration.upgrade(listOf(old("hibiki-0")), setOf("hibiki"), defaults)
        val edited = first.lines.map { it.copy(text = "Edited after upgrade") }
        assertEquals(edited, CatalogMigration.upgrade(edited, first.enabledCharacters, defaults).lines)
    }

    @Test fun unknownRemovedCharactersCannotReachNotificationSelection() {
        assertNull(DialoguePicker.pick(listOf(Dialogue("old", "hibiki", "Old reminder")), setOf("hibiki")))
    }
}

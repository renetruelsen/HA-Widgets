package dk.akait.hawidgets.transfer

import dk.akait.hawidgets.data.DisplayMode
import dk.akait.hawidgets.data.WidgetConfig
import dk.akait.hawidgets.data.db.MultiWidgetSlotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetTransferSerializerTest {

    private fun slot(index: Int) = MultiWidgetSlotEntity(
        appWidgetId = 42, // eksporteres bevidst IKKE — skal blive 0 ved parse
        slotIndex = index,
        displayEntityId = "sensor.termometer_temperature",
        displayDomain = "sensor",
        actionEntityId = "light.stuelampe",
        actionDomain = "light",
        action = "TOGGLE",
        label = "Stue",
        confirmAction = true,
        displayPrecision = 1,
        datetimeFormat = "dd/MM",
        rangeInputMode = null,
        showIcon = false,
        actionPackageName = null,
        secondary1DisplayEntityId = "switch.spa",
        secondary1DisplayDomain = "switch",
        secondary1ActionEntityId = "switch.spa",
        secondary1ActionDomain = "switch",
        secondary1Action = "TOGGLE",
        secondary1ShowValue = true,
        secondary1Label = "Spa",
    )

    private fun bundle() = TransferBundle(
        exported = "2026-07-13T14:00:00Z",
        configs = listOf(
            TransferConfig.Multi(label = "Multi: Stue", showRefreshIcon = false, slots = listOf(slot(0), slot(1))),
            TransferConfig.Shortcut(
                label = "Hjem",
                config = WidgetConfig("lovelace-hjem", "Hjem", DisplayMode.OVERLAY, 90, 80),
            ),
        ),
    )

    private fun parseOk(raw: String): TransferBundle =
        parseTransferBundle(raw).getOrElse { throw AssertionError("expected success, got $it") }

    @Test fun roundTripPreservesMultiAndShortcut() {
        val original = bundle()
        val parsed = parseOk(serializeTransferBundle(original))

        assertEquals("2026-07-13T14:00:00Z", parsed.exported)
        assertEquals(2, parsed.configs.size)

        val multi = parsed.multiConfigs.single()
        assertEquals("Multi: Stue", multi.label)
        assertEquals(false, multi.showRefreshIcon)
        assertEquals(2, multi.slots.size)

        val s0 = multi.slots[0]
        assertEquals(0, s0.appWidgetId) // placeholder — ikke afsenderens 42
        assertEquals("sensor.termometer_temperature", s0.displayEntityId)
        assertEquals("light.stuelampe", s0.actionEntityId)
        assertEquals("TOGGLE", s0.action)
        assertEquals("Stue", s0.label)
        assertEquals(true, s0.confirmAction)
        assertEquals(1, s0.displayPrecision)
        assertEquals("dd/MM", s0.datetimeFormat)
        assertEquals(false, s0.showIcon)
        assertNull(s0.rangeInputMode)
        // Sekundær-chip bevaret
        assertEquals("switch.spa", s0.secondary1DisplayEntityId)
        assertEquals("TOGGLE", s0.secondary1Action)
        assertEquals(true, s0.secondary1ShowValue)
        assertEquals("Spa", s0.secondary1Label)
        // Tom 2. plads forbliver null
        assertNull(s0.secondary2DisplayEntityId)

        val shortcut = parsed.shortcutConfigs.single()
        assertEquals("Hjem", shortcut.label)
        assertEquals("lovelace-hjem", shortcut.config.dashboardPath)
        assertEquals(DisplayMode.OVERLAY, shortcut.config.displayMode)
        assertEquals(90, shortcut.config.widthPct)
    }

    @Test fun invalidJsonFails() {
        assertEquals(ImportError.InvalidJson, errorOf("{ not json"))
    }

    @Test fun wrongAppTagFails() {
        val raw = """{"version":1,"app":"other-app","exported":"x","configs":[{"type":"shortcut","config":{}}]}"""
        assertEquals(ImportError.WrongApp, errorOf(raw))
    }

    @Test fun newerVersionFails() {
        val raw = """{"version":99,"app":"ha-widgets","exported":"x","configs":[{"type":"shortcut","config":{}}]}"""
        assertEquals(ImportError.UnsupportedVersion(99), errorOf(raw))
    }

    @Test fun emptyConfigsFails() {
        val raw = """{"version":1,"app":"ha-widgets","exported":"x","configs":[]}"""
        assertEquals(ImportError.NoConfigs, errorOf(raw))
    }

    @Test fun unknownConfigTypesIgnoredThenNoConfigs() {
        val raw = """{"version":1,"app":"ha-widgets","exported":"x","configs":[{"type":"bogus"}]}"""
        assertEquals(ImportError.NoConfigs, errorOf(raw))
    }

    @Test fun missingFieldsFallBackToDefaults() {
        // Minimal multi-slot: kun de nødvendige felter — resten skal falde tilbage til defaults.
        val raw = """
            {"version":1,"app":"ha-widgets","exported":"x","configs":[
              {"type":"multi","label":"M","slots":[{"displayEntityId":"light.x","displayDomain":"light"}]}
            ]}
        """.trimIndent()
        val multi = parseOk(raw).multiConfigs.single()
        assertEquals(true, multi.showRefreshIcon) // default
        val s = multi.slots.single()
        assertEquals("NONE", s.action)
        assertEquals(true, s.showIcon)
        assertEquals(false, s.confirmAction)
        assertEquals(0, s.slotIndex) // fallback = array-index
        assertTrue(s.secondary1DisplayEntityId == null)
    }

    private fun errorOf(raw: String): ImportError {
        val ex = parseTransferBundle(raw).exceptionOrNull()
        assertTrue("expected ImportException, got $ex", ex is ImportException)
        return (ex as ImportException).error
    }
}

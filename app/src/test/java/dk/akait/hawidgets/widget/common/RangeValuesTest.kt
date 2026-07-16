package dk.akait.hawidgets.widget.common

import dk.akait.hawidgets.data.db.EntityStateEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class RangeValuesTest {

    private fun state(value: String, attrs: String = "{}") =
        EntityStateEntity(entityId = "x", state = value, attributesJson = attrs, lastUpdated = 0L)

    @Test fun lightBrightnessToPercent() {
        val s = state("on", """{"brightness":128}""")
        val value = rangeCurrentValue("light", s, JSONObject(s.attributesJson))
        assertEquals(50.0, value, 0.5)
    }

    @Test fun coverPositionFallsBackToOpenClosedWhenMissing() {
        val open = state("open")
        assertEquals(100.0, rangeCurrentValue("cover", open, JSONObject()), 0.0)
        val closed = state("closed")
        assertEquals(0.0, rangeCurrentValue("cover", closed, JSONObject()), 0.0)
    }

    @Test fun climateUsesTargetTemperature() {
        val s = state("heat", """{"temperature":21}""")
        assertEquals(21.0, rangeCurrentValue("climate", s, JSONObject(s.attributesJson)), 0.0)
    }

    @Test fun numberPreservesDecimal() {
        val s = state("37.5")
        assertEquals(37.5, rangeCurrentValue("number", s, JSONObject()), 0.0)
    }

    @Test fun formatRangeValueLightAndCoverShowPercent() {
        val light = state("on", """{"brightness":255}""")
        assertEquals("100%", formatRangeValue("light", light))
        val cover = state("open", """{"current_position":42}""")
        assertEquals("42%", formatRangeValue("cover", cover))
    }

    @Test fun formatRangeValueClimateShowsWholeDegreesWithoutDecimal() {
        val s = state("heat", """{"temperature":21}""")
        assertEquals("21°", formatRangeValue("climate", s))
    }

    @Test fun minMaxDefaultsForClimate() {
        assertEquals(16.0, rangeMin("climate", JSONObject()), 0.0)
        assertEquals(30.0, rangeMax("climate", JSONObject()), 0.0)
    }

    @Test fun minMaxForNumberReadFromAttrs() {
        val attrs = JSONObject().put("min", 5.0).put("max", 95.0)
        assertEquals(5.0, rangeMin("number", attrs), 0.0)
        assertEquals(95.0, rangeMax("number", attrs), 0.0)
    }
}

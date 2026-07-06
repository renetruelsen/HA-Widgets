package dk.akait.hawidgets.widget.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ValueFormattingTest {
    // Numerisk
    @Test fun autoRoundsToOneDecimal() = assertEquals("23.9", formatNumericState("23.88888888888", null))
    @Test fun autoDropsTrailingZeroDecimal() = assertEquals("24", formatNumericState("24.0", null))
    @Test fun integerStaysInteger() = assertEquals("42", formatNumericState("42", null))
    @Test fun precisionZeroRounds() = assertEquals("24", formatNumericState("23.9", 0))
    @Test fun precisionTwoKeeps() = assertEquals("23.89", formatNumericState("23.88888", 2))
    @Test fun precisionPadsZeros() = assertEquals("24.00", formatNumericState("24", 2))
    @Test fun nonNumericPassthrough() = assertEquals("1.2.3", formatNumericState("1.2.3", null))
    @Test fun usesDotNeverComma() = assertEquals("23.9", formatNumericState("23.88", 1)) // uanset enheds-locale

    // Datetime — HA-format "2026-07-04 15:30:00" (input_datetime) og ISO-8601 (timestamp-sensorer)
    @Test fun patternApplied() =
        assertEquals("04/07 15:30", formatDateTimeState("2026-07-04 15:30:00", "dd/MM HH:mm", true, true, Locale.ROOT))
    @Test fun isoTimestampParsed() =
        assertEquals("04/07 15:30", formatDateTimeState("2026-07-04T15:30:00+02:00", "dd/MM HH:mm", true, true, Locale.ROOT))
    @Test fun invalidPatternFallsBackToAuto() {
        // First verify that "VVVV-ugyldig" is indeed an invalid pattern
        try {
            java.time.format.DateTimeFormatter.ofPattern("VVVV-ugyldig", Locale.ROOT)
            throw AssertionError("Expected IllegalArgumentException for invalid pattern 'VVVV-ugyldig'")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        val out = formatDateTimeState("2026-07-04 15:30:00", "VVVV-ugyldig", true, true, Locale.ROOT)
        assertEquals(autoDateTime("2026-07-04 15:30:00", true, true, Locale.ROOT), out)
    }
    @Test fun unparsableStatePassthrough() =
        assertEquals("unknown", formatDateTimeState("unknown", "dd/MM", true, true, Locale.ROOT))
    @Test fun timeOnlyEntity() =
        assertEquals("15:30", formatDateTimeState("15:30:00", null, false, true, Locale.ROOT))

    @Test fun timestampSensorDetected() =
        assertEquals(true, isDateTimeLike("sensor", """{"device_class":"timestamp"}"""))
    @Test fun inputDatetimeDetected() = assertEquals(true, isDateTimeLike("input_datetime", null))
    @Test fun plainSensorNotDateTime() = assertEquals(false, isDateTimeLike("sensor", """{"device_class":"temperature"}"""))

    // formatDisplayValue — samlet visnings-værdi (auto/precision-afrunding + enhed, eller datetime)
    @Test fun displayValueRoundsAutoAndAppendsUnit() =
        assertEquals("23.9 °C", formatDisplayValue("sensor", "23.888", """{"unit_of_measurement":"°C"}""", null, null, Locale.ROOT))

    @Test fun displayValuePrecisionOverride() =
        assertEquals("24 °C", formatDisplayValue("sensor", "23.888", """{"unit_of_measurement":"°C"}""", 0, null, Locale.ROOT))

    @Test fun displayValueNoUnitAttribute() =
        assertEquals("23.9", formatDisplayValue("sensor", "23.888", "{}", null, null, Locale.ROOT))

    @Test fun displayValueNullAttributesJson() =
        assertEquals("23.9", formatDisplayValue("sensor", "23.888", null, null, null, Locale.ROOT))

    @Test fun displayValueDateTimeLikeWithCustomPattern() =
        assertEquals(
            "04/07 15:30",
            formatDisplayValue("input_datetime", "2026-07-04 15:30:00", """{"has_date":true,"has_time":true}""", null, "dd/MM HH:mm", Locale.ROOT),
        )

    @Test fun displayValueDateTimeLikeInvalidPatternFallsBackToAuto() {
        val state = "2026-07-04 15:30:00"
        val attrs = """{"has_date":true,"has_time":true}"""
        val out = formatDisplayValue("input_datetime", state, attrs, null, "VVVV-ugyldig", Locale.ROOT)
        assertEquals(autoDateTime(state, true, true, Locale.ROOT), out)
    }

    @Test fun displayValueDateTimeLikeAutoWhenPatternNull() =
        assertEquals(
            autoDateTime("2026-07-04 15:30:00", true, true, Locale.ROOT),
            formatDisplayValue("input_datetime", "2026-07-04 15:30:00", """{"has_date":true,"has_time":true}""", null, null, Locale.ROOT),
        )

    @Test fun displayValueNullStateShowsPlaceholder() =
        assertEquals("…", formatDisplayValue("sensor", null, null, null, null, Locale.ROOT))

    @Test fun displayValueUnavailableStateIsBlank() =
        assertEquals("", formatDisplayValue("sensor", "unavailable", """{"unit_of_measurement":"°C"}""", null, null, Locale.ROOT))
}

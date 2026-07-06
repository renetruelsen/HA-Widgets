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
}

package dk.rtr.hawidgets.widget.common

import org.junit.Assert.assertEquals
import org.junit.Test

class RangeSteppingTest {
    // stepFor: lille range (≤ 20) → 0.5, ellers 1.0
    @Test fun smallRangeUsesHalfStep() = assertEquals(0.5, stepFor(16.0, 30.0), 0.0)
    @Test fun exactlyTwentyUsesHalfStep() = assertEquals(0.5, stepFor(0.0, 20.0), 0.0)
    @Test fun largeRangeUsesWholeStep() = assertEquals(1.0, stepFor(0.0, 100.0), 0.0)
    @Test fun justOverTwentyUsesWholeStep() = assertEquals(1.0, stepFor(0.0, 20.5), 0.0)

    // stepValue: første tryk snapper til nærmeste trin, derefter ét trin i retningen, clamped.
    @Test fun minusRoundsToNearestStep() = assertEquals(23.5, stepValue(23.7, -1, 0.5, 16.0, 30.0), 0.0)
    @Test fun plusFromRoundValue() = assertEquals(24.0, stepValue(23.5, +1, 0.5, 16.0, 30.0), 0.0)
    @Test fun plusFromOffStepValue() = assertEquals(24.0, stepValue(23.7, +1, 0.5, 16.0, 30.0), 0.0)
    @Test fun minusFromRoundValue() = assertEquals(23.0, stepValue(23.5, -1, 0.5, 16.0, 30.0), 0.0)
    @Test fun clampsAtMax() = assertEquals(30.0, stepValue(30.0, +1, 0.5, 16.0, 30.0), 0.0)
    @Test fun clampsAtMin() = assertEquals(16.0, stepValue(16.0, -1, 0.5, 16.0, 30.0), 0.0)

    // Heltalstrin (fx cover 0–100, step 1.0)
    @Test fun wholeStepPlus() = assertEquals(51.0, stepValue(50.0, +1, 1.0, 0.0, 100.0), 0.0)
    @Test fun wholeStepMinusFromOffStep() = assertEquals(50.0, stepValue(50.4, -1, 1.0, 0.0, 100.0), 0.0)
    @Test fun wholeStepPlusFromOffStep() = assertEquals(51.0, stepValue(50.4, +1, 1.0, 0.0, 100.0), 0.0)
    @Test fun wholeStepMinusRoundsHalfUpThenNoExtra() =
        // 50.5 snapper til 51 (Math.round HALF_UP), snapped != current + retning<0 ⇒ 51.0
        assertEquals(51.0, stepValue(50.5, -1, 1.0, 0.0, 100.0), 0.0)
}

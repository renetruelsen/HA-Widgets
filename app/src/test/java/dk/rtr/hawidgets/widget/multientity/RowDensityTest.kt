package dk.rtr.hawidgets.widget.multientity

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pinner densitets→indholdshøjde-afbildningen (44/48/52dp) — en refaktor må ikke stille flytte den. */
class RowDensityTest {

    @Test fun compactIs44() {
        assertEquals(44, RowDensity.contentHeightDp(RowDensity.COMPACT))
    }

    @Test fun normalIs48() {
        assertEquals(48, RowDensity.contentHeightDp(RowDensity.NORMAL))
    }

    @Test fun largeIs52() {
        assertEquals(52, RowDensity.contentHeightDp(RowDensity.LARGE))
    }

    @Test fun nullAndUnknownFallBackToNormal() {
        assertEquals(48, RowDensity.contentHeightDp(null))
        assertEquals(48, RowDensity.contentHeightDp("GARBAGE"))
    }

    @Test fun defaultIsNormal() {
        assertEquals(RowDensity.NORMAL, RowDensity.DEFAULT)
        assertEquals(listOf("COMPACT", "NORMAL", "LARGE"), RowDensity.ALL)
    }
}

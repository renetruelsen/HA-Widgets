package dk.akait.hawidgets.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetReconcilerTest {
    private val grace = WIDGET_ORPHAN_GRACE_MILLIS
    private val now = 1_000_000_000_000L

    @Test fun boundUnstampedDoesNothing() =
        assertEquals(ReconcileAction.NONE, reconcileDecision(bound = true, removedAt = null, now = now, graceMillis = grace))

    @Test fun boundStampedClears() =
        assertEquals(ReconcileAction.CLEAR, reconcileDecision(bound = true, removedAt = now - 1000, now = now, graceMillis = grace))

    @Test fun unboundUnstampedStamps() =
        assertEquals(ReconcileAction.STAMP, reconcileDecision(bound = false, removedAt = null, now = now, graceMillis = grace))

    @Test fun unboundWithinGraceDoesNothing() =
        assertEquals(ReconcileAction.NONE, reconcileDecision(bound = false, removedAt = now - grace + 1000, now = now, graceMillis = grace))

    @Test fun unboundExactlyAtGraceDoesNothing() =
        // > grace er strengt, så præcis grace er stadig i vinduet
        assertEquals(ReconcileAction.NONE, reconcileDecision(bound = false, removedAt = now - grace, now = now, graceMillis = grace))

    @Test fun unboundPastGracePurges() =
        assertEquals(ReconcileAction.PURGE, reconcileDecision(bound = false, removedAt = now - grace - 1, now = now, graceMillis = grace))
}

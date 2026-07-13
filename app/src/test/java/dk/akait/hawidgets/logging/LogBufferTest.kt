package dk.akait.hawidgets.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LogBufferTest {

    private val ts = Instant.parse("2026-07-12T10:00:00.000Z")

    @Test
    fun formatLogLineProducesExactShape() {
        val line = formatLogLine('E', "HA", "SocketException: Connection reset", ts)
        assertEquals("2026-07-12T10:00:00.000Z E [HA] SocketException: Connection reset", line)
    }

    @Test
    fun formatLogLinePreservesMillis() {
        val withMillis = Instant.parse("2026-07-12T10:00:04.512Z")
        val line = formatLogLine('W', "HA", "Retry 2/3 GET /api/states", withMillis)
        assertEquals("2026-07-12T10:00:04.512Z W [HA] Retry 2/3 GET /api/states", line)
    }

    @Test
    fun addAppendsFormattedLine() {
        val buffer = LogBuffer(maxLines = 10)
        buffer.add('I', "BOOT", "Widget host ready", ts)
        assertEquals(
            listOf("2026-07-12T10:00:00.000Z I [BOOT] Widget host ready"),
            buffer.snapshot()
        )
    }

    @Test
    fun ringBufferEvictsOldestWhenOverCap() {
        val buffer = LogBuffer(maxLines = 3)
        repeat(5) { i -> buffer.add('I', "T", "line$i", ts) }
        val snapshot = buffer.snapshot()
        assertEquals(3, snapshot.size)
        assertTrue(snapshot[0].endsWith("line2"))
        assertTrue(snapshot[2].endsWith("line4"))
    }

    @Test
    fun addRawAppendsUnformattedLine() {
        val buffer = LogBuffer(maxLines = 10)
        buffer.addRaw("    #0 _HttpClient.send (package:x)")
        assertEquals(listOf("    #0 _HttpClient.send (package:x)"), buffer.snapshot())
    }

    @Test
    fun bodyJoinsLinesWithNewline() {
        val buffer = LogBuffer(maxLines = 10)
        buffer.add('I', "A", "one", ts)
        buffer.add('I', "B", "two", ts)
        assertEquals(
            "2026-07-12T10:00:00.000Z I [A] one\n2026-07-12T10:00:00.000Z I [B] two",
            buffer.body()
        )
    }

    @Test
    fun isEmptyReflectsBufferState() {
        val buffer = LogBuffer(maxLines = 10)
        assertTrue(buffer.isEmpty())
        buffer.add('I', "A", "one", ts)
        assertTrue(!buffer.isEmpty())
    }

    @Test
    fun clearEmptiesBuffer() {
        val buffer = LogBuffer(maxLines = 10)
        buffer.add('I', "A", "one", ts)
        buffer.clear()
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun snapshotWithLimitReturnsOnlyLastNLines() {
        val buffer = LogBuffer(maxLines = 10)
        repeat(5) { i -> buffer.add('I', "T", "line$i", ts) }
        val last3 = buffer.snapshot(3)
        assertEquals(3, last3.size)
        assertTrue(last3[0].endsWith("line2"))
        assertTrue(last3[2].endsWith("line4"))
    }

    @Test
    fun snapshotWithLimitLargerThanSizeReturnsAll() {
        val buffer = LogBuffer(maxLines = 10)
        buffer.add('I', "A", "one", ts)
        assertEquals(1, buffer.snapshot(30).size)
    }
}

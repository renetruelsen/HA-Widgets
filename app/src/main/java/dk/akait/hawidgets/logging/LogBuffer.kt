package dk.akait.hawidgets.logging

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val TIMESTAMP_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

/**
 * Ren linjeformatering til rtr.dk Log Collector (docs/ha-widgets-logging.md):
 * `<ISO-8601 UTC med millisekunder> <NIVEAU> [<TAG>] <besked>`. Kun linjer med literalt
 * " E [" udløser Fejl-status/mail server-side, så niveau-valg pr. kaldested er vigtigt
 * (se HaApiClient — forbindelsesfejl er bevidst W, ikke E).
 */
fun formatLogLine(level: Char, tag: String, message: String, timestamp: Instant = Instant.now()): String =
    "${TIMESTAMP_FORMATTER.format(timestamp)} $level [$tag] $message"

/**
 * Ring-buffer af log-linjer der skal uploades. Cap på [maxLines] — ældste linje falder ud
 * først. Thread-safe (kaldes både fra UI/IO-coroutines og fra en crashende tråd).
 */
class LogBuffer(private val maxLines: Int = 300) {
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun add(level: Char, tag: String, message: String, timestamp: Instant = Instant.now()) {
        addRaw(formatLogLine(level, tag, message, timestamp))
    }

    /** Tilføjer en linje uden formatering — bruges til fri tekst som stacktraces. */
    @Synchronized
    fun addRaw(line: String) {
        lines.addLast(line)
        while (lines.size > maxLines) lines.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    @Synchronized
    fun body(): String = lines.joinToString("\n")

    @Synchronized
    fun isEmpty(): Boolean = lines.isEmpty()

    @Synchronized
    fun clear() = lines.clear()
}

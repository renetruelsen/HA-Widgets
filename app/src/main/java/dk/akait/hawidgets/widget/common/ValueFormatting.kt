package dk.akait.hawidgets.widget.common

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Afrund numerisk HA-state. precision=null → auto: maks 1 decimal, heltal forbliver heltal.
 *  Punktum altid decimalseparator (HA-state er Locale.ROOT; visning m. komma brød trim i v0.2.34). */
fun formatNumericState(state: String, precision: Int?): String {
    val value = state.toBigDecimalOrNull() ?: return state
    return if (precision == null) {
        val rounded = value.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros()
        rounded.toPlainString()
    } else {
        value.setScale(precision, RoundingMode.HALF_UP).toPlainString()
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? = try { BigDecimal(trim()) } catch (_: Exception) { null }

private val HA_LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun parseHaDateTime(state: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(state, HA_LOCAL) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(state).toLocalDateTime() }.getOrNull()
        ?: runCatching { LocalDate.parse(state).atStartOfDay() }.getOrNull()
        ?: runCatching { LocalTime.parse(state).atDate(LocalDate.now()) }.getOrNull()

/** Auto-format: lokalt kort format udledt af has_date/has_time. */
fun autoDateTime(state: String, hasDate: Boolean, hasTime: Boolean, locale: Locale): String {
    val dt = parseHaDateTime(state) ?: return state
    val fmt = when {
        hasDate && hasTime -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        hasDate -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
        else -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    }.withLocale(locale)
    return fmt.format(dt)
}

/** Frit DateTimeFormatter-mønster; null/tom/ugyldig → auto; uparsbar state → passthrough. */
fun formatDateTimeState(state: String, pattern: String?, hasDate: Boolean, hasTime: Boolean, locale: Locale): String {
    val dt = parseHaDateTime(state) ?: return state
    if (pattern.isNullOrBlank()) return autoDateTime(state, hasDate, hasTime, locale)
    return runCatching { DateTimeFormatter.ofPattern(pattern, locale).format(dt) }
        .getOrElse { autoDateTime(state, hasDate, hasTime, locale) }
}

/** Er entiteten datetime-agtig (frit datoformat-felt vises i config)? */
fun isDateTimeLike(domain: String, attributesJson: String?): Boolean {
    if (domain == "input_datetime") return true
    if (domain != "sensor") return false
    if (attributesJson == null) return false
    return try {
        val json = JSONObject(attributesJson)
        val deviceClass = json.optString("device_class")
        deviceClass == "timestamp"
    } catch (_: Exception) {
        false
    }
}

package dk.rtr.hawidgets.widget.common

import dk.rtr.hawidgets.data.db.EntityStateEntity
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

/** Domæner der har en RANGE-handling MEN ikke er et rå værdi-domæn ([isRawValueDomain]) — dvs.
 * de har en fast tekst-tabel i [formatEntityState] normalt, men kan vælges til i stedet at vise
 * deres faktiske skyder-værdi (v0.3.x, punkt 3). */
val RANGE_VALUE_DOMAINS = setOf("light", "cover", "climate")

/** Domæne-specifik udtrækning af RANGE-handlingens "aktuelle værdi" fra en entitets state/attrs —
 * delt mellem klik-dialogen ([dk.rtr.hawidgets.widget.multientity.clickModifier]) og den
 * "vis rå værdi"-visning ([formatRangeValue]), så de aldrig kan divergere på hvad værdien er. */
fun rangeCurrentValue(domain: String, state: EntityStateEntity, attrs: JSONObject): Double = when (domain) {
    "light" -> attrs.optInt("brightness", 255).let { (it * 100 / 255).coerceIn(0, 100) }.toDouble()
    "cover" -> attrs.optInt("current_position", if (state.state == "open") 100 else 0).toDouble()
    "climate" -> attrs.optInt("temperature", 20).toDouble()
    // Bevarer decimaler (fx 21.5) i stedet for at afrunde til et heltal — number/input_number
    // kan have en fraktioneret step.
    "number", "input_number" -> state.state.toDoubleOrNull() ?: 0.0
    else -> 0.0
}

fun rangeMin(domain: String, attrs: JSONObject): Double = when (domain) {
    "climate" -> attrs.optInt("min_temp", 16).toDouble()
    "number", "input_number" -> attrs.optDouble("min", 0.0)
    else -> 1.0
}

fun rangeMax(domain: String, attrs: JSONObject): Double = when (domain) {
    "climate" -> attrs.optInt("max_temp", 30).toDouble()
    "number", "input_number" -> attrs.optDouble("max", 100.0)
    else -> 100.0
}

/** Formatterer et domænes RANGE-værdi som visningstekst ("45%" for lys/persienne, "21.5°" for
 * klima) — bruges når brugeren har slået "Vis værdi" til for et domæne i [RANGE_VALUE_DOMAINS],
 * i stedet for [formatEntityState]s faste tekst ("Tændt"/"Åben"/"Varme" osv.). */
fun formatRangeValue(domain: String, state: EntityStateEntity): String {
    val attrs = try { JSONObject(state.attributesJson) } catch (_: Exception) { JSONObject() }
    val value = rangeCurrentValue(domain, state, attrs)
    return when (domain) {
        "light", "cover" -> "${value.roundToInt()}%"
        "climate" -> formatDegrees(value)
        else -> value.toString()
    }
}

private fun formatDegrees(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded == rounded.toInt().toDouble()) {
        "${rounded.toInt()}°"
    } else {
        String.format(Locale.ROOT, "%.1f°", rounded)
    }
}

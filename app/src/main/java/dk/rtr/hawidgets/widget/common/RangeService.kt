package dk.rtr.hawidgets.widget.common

import android.content.Context
import dk.rtr.hawidgets.data.EntityRepository
import dk.rtr.hawidgets.data.HaApiClient
import kotlin.math.roundToInt

/**
 * Delt RANGE-værdi-afsendelse: én kanonisk domain→service-mapping for at SÆTTE en numerisk værdi
 * på en entitet (lys-lysstyrke, cover-position, klima-temperatur, number/input_number-værdi).
 *
 * Task 13 (del A) tilføjede [dk.rtr.hawidgets.widget.common.NumberInputActivity] som et alternativ
 * til [RangeControlActivity]'s skyder. Begge afsender NØJAGTIG samme service-kald — for at undgå en
 * TREDJE nær-duplikeret domain→service-mapping (efter EntityActions.kt og Task 8's
 * ConfirmActionActivity — begge de mappinger er dog TOGGLE/TRIGGER, ikke RANGE) er RANGE-mappingen
 * ekstraheret hertil og kaldes fra begge Activities i stedet for at være kopieret.
 *
 * light/cover/climate afrunder til heltal (uændret historisk adfærd — brightness-%, position, °C);
 * number/input_number sender den fulde decimalværdi (bevarer step 0.5 osv., jf. v0.2.34).
 */
suspend fun sendRangeValue(context: Context, domain: String, entityId: String, value: Double): Boolean {
    val api = resolveHaApiClient(context) ?: return false
    val result = when (domain) {
        "light" -> api.callService(
            "light", "turn_on", entityId,
            // Afrunding (ikke trunkering), så fx 50% → 128 og læses tilbage som 50%, jf.
            // rangeCurrentValue — undgår "ned med 1%" ved genlæsning.
            extraData = mapOf("brightness" to (value * 255.0 / 100.0).roundToInt().coerceIn(1, 255)),
        )
        "cover" -> api.callService(
            "cover", "set_cover_position", entityId,
            extraData = mapOf("position" to value.toInt()),
        )
        "climate" -> api.callService(
            "climate", "set_temperature", entityId,
            extraData = mapOf("temperature" to value.toInt()),
        )
        "number" -> api.callService(
            "number", "set_value", entityId,
            extraData = mapOf("value" to value),
        )
        "input_number" -> api.callService(
            "input_number", "set_value", entityId,
            extraData = mapOf("value" to value),
        )
        else -> return false
    }
    val ok = result is HaApiClient.Result.Ok
    if (ok) {
        EntityRepository.refresh(context.applicationContext, entityId)
        // Fysiske RANGE-mål (klima-temperatur, Velux-position, lys-dæmpning) lander først
        // sekunder-til-minutter efter kommandoen → efterpoll så den nye tilstand fanges uden
        // at vente på næste periodiske sync.
        EntityRepository.scheduleSettleBurst(context, entityId)
    }
    return ok
}

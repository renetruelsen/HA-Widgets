package dk.akait.hawidgets.widget.common

import android.content.Context
import dk.akait.hawidgets.data.EntityRepository
import dk.akait.hawidgets.data.HaApiClient
import dk.akait.hawidgets.data.SecureStore

/**
 * Delt RANGE-værdi-afsendelse: én kanonisk domain→service-mapping for at SÆTTE en numerisk værdi
 * på en entitet (lys-lysstyrke, cover-position, klima-temperatur, number/input_number-værdi).
 *
 * Task 13 (del A) tilføjede [dk.akait.hawidgets.widget.common.NumberInputActivity] som et alternativ
 * til [RangeControlActivity]'s skyder. Begge afsender NØJAGTIG samme service-kald — for at undgå en
 * TREDJE nær-duplikeret domain→service-mapping (efter EntityActions.kt og Task 8's
 * ConfirmActionActivity — begge de mappinger er dog TOGGLE/TRIGGER, ikke RANGE) er RANGE-mappingen
 * ekstraheret hertil og kaldes fra begge Activities i stedet for at være kopieret.
 *
 * light/cover/climate afrunder til heltal (uændret historisk adfærd — brightness-%, position, °C);
 * number/input_number sender den fulde decimalværdi (bevarer step 0.5 osv., jf. v0.2.34).
 */
suspend fun sendRangeValue(context: Context, domain: String, entityId: String, value: Double) {
    val store = SecureStore.get(context.applicationContext)
    val base = store.baseUrl ?: return
    val token = store.token ?: return
    val api = HaApiClient(base, token)
    when (domain) {
        "light" -> api.callService(
            "light", "turn_on", entityId,
            extraData = mapOf("brightness" to (value.toInt() * 255 / 100).coerceIn(1, 255)),
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
    }
    EntityRepository.refresh(context.applicationContext, entityId)
}

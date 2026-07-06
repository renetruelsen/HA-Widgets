package dk.akait.hawidgets.widget.common

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dk.akait.hawidgets.R
import dk.akait.hawidgets.data.EntityRepository
import dk.akait.hawidgets.data.db.AppDatabase
import kotlinx.coroutines.launch

/**
 * Bekræft-dialog for MultiEntityWidget-slots/-chips med "Bekræft ved tryk" slået til (B1, v0.3.0).
 *
 * Følger samme dialog-aktivitets-mønster som [RangeControlActivity]/[TextControlActivity]
 * (translucent tema, Material3 Surface-dialog, bootstrap kopieret — kendt duplikeret mønster,
 * accepteret i v0.2.34-fund #7).
 *
 * ADR-1: dialogens tekst navngiver ALTID handlings-målet ([EXTRA_LABEL] = action-entitetens
 * friendly name), aldrig den viste entitet — de kan afvige (visning ≠ handling).
 *
 * Ved "Bekræft" udføres NØJAGTIG samme service-kald som den direkte (uden-bekræft) klik-vej i
 * [dk.akait.hawidgets.widget.multientity.MultiEntityWidget] — dvs. [ToggleEntityAction] for
 * "TOGGLE" og [TriggerEntityAction] for "TRIGGER". Service-mappingen er kopieret 1:1; se
 * [executeConfirmedAction].
 */
class ConfirmActionActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ENTITY_ID = "entity_id"
        const val EXTRA_DOMAIN = "domain"
        const val EXTRA_LABEL = "label"
        const val EXTRA_ACTION = "action"
        const val EXTRA_IS_ON = "is_on"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val entityId = intent.getStringExtra(EXTRA_ENTITY_ID) ?: return finish()
        val domain = intent.getStringExtra(EXTRA_DOMAIN) ?: return finish()
        val label = intent.getStringExtra(EXTRA_LABEL) ?: entityId
        val action = intent.getStringExtra(EXTRA_ACTION) ?: "TOGGLE"
        val isOn = intent.getBooleanExtra(EXTRA_IS_ON, false)

        setContent {
            MaterialTheme {
                Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                    val scope = rememberCoroutineScope()
                    var busy by remember { mutableStateOf(false) }

                    val question = when {
                        action == "TRIGGER" -> stringResource(R.string.confirm_trigger, label)
                        isOn -> stringResource(R.string.confirm_toggle_off, label)
                        else -> stringResource(R.string.confirm_toggle_on, label)
                    }

                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(question, style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            OutlinedButton(onClick = { finish() }, enabled = !busy) {
                                Text(stringResource(R.string.confirm_dialog_cancel))
                            }
                            Spacer(Modifier.padding(4.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        executeConfirmedAction(applicationContext, domain, action, entityId)
                                        finish()
                                    }
                                },
                                enabled = !busy,
                            ) {
                                Text(stringResource(R.string.confirm_dialog_confirm))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Udfører den bekræftede handling med NØJAGTIG samme service-mapping som den direkte klik-vej.
 *
 * - "TOGGLE": kopieret 1:1 fra [ToggleEntityAction.onAction] — læser frisk DB-state, domain-bevidst
 *   mapping (lock → lock/unlock, cover → open_cover/close_cover, ellers → turn_on/turn_off), kalder
 *   [EntityRepository.command] med samme argumenter (inkl. optimistisk state, som ligger inde i
 *   `command` selv). Returnerer tavst hvis entiteten ikke har en cached state (samme som Toggle-
 *   actionen: `?: return`).
 * - "TRIGGER": kopieret 1:1 fra service-udledningen i MultiEntityWidget.clickModifier
 *   (automation → trigger, input_button → press, ellers turn_on for scene/script) + selve kaldet
 *   fra [TriggerEntityAction.onAction] (`targetState = current?.state ?: "on"`, ingen eksplicit
 *   targetState sendes fra clickModifier).
 */
internal suspend fun executeConfirmedAction(
    context: Context,
    domain: String,
    action: String,
    entityId: String,
) {
    val stateDao = AppDatabase.get(context).entityStateDao()
    if (action == "TRIGGER") {
        // Kopieret fra MultiEntityWidget.clickModifier "else -> // TRIGGER"-grenen.
        val service = when (domain) {
            "automation" -> "trigger"
            "input_button" -> "press"
            else -> "turn_on" // scene, script
        }
        // Kopieret fra TriggerEntityAction.onAction (uden eksplicit targetState).
        val current = stateDao.get(entityId)
        EntityRepository.command(
            context = context,
            domain = domain,
            service = service,
            entityId = entityId,
            targetState = current?.state ?: "on",
            fromState = current?.state,
        )
    } else {
        // "TOGGLE" — kopieret 1:1 fra ToggleEntityAction.onAction.
        val current = stateDao.get(entityId) ?: return
        val (targetState, service) = when (domain) {
            "lock" -> if (current.state == "locked") "unlocked" to "unlock" else "locked" to "lock"
            "cover" -> if (current.state == "open") "closed" to "close_cover" else "open" to "open_cover"
            else -> if (current.state == "on") "off" to "turn_off" else "on" to "turn_on"
        }
        EntityRepository.command(context, domain, service, entityId, targetState, current.state)
    }
}

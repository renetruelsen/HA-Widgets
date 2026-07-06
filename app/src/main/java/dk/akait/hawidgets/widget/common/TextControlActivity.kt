package dk.akait.hawidgets.widget.common

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dk.akait.hawidgets.ui.theme.HaWidgetsTheme
import dk.akait.hawidgets.data.EntityRepository
import dk.akait.hawidgets.data.HaApiClient
import dk.akait.hawidgets.data.SecureStore
import kotlinx.coroutines.launch

/** Redigér en input_text-entitets værdi — simpel tekstboks + Gem, kalder input_text.set_value. */
class TextControlActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ENTITY_ID = "entity_id"
        const val EXTRA_LABEL = "label"
        const val EXTRA_CURRENT_VALUE = "current_value"
        const val EXTRA_MAX_LENGTH = "max_length"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val entityId = intent.getStringExtra(EXTRA_ENTITY_ID) ?: return finish()
        val label = intent.getStringExtra(EXTRA_LABEL) ?: entityId
        val initialValue = intent.getStringExtra(EXTRA_CURRENT_VALUE) ?: ""
        val maxLength = intent.getIntExtra(EXTRA_MAX_LENGTH, 255).takeIf { it > 0 } ?: 255

        setContent {
            HaWidgetsTheme {
                Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                    val scope = rememberCoroutineScope()
                    var text by remember { mutableStateOf(initialValue) }
                    var busy by remember { mutableStateOf(false) }

                    fun save() {
                        scope.launch {
                            busy = true
                            val store = SecureStore.get(applicationContext)
                            val base = store.baseUrl
                            val token = store.token
                            if (base != null && token != null) {
                                HaApiClient(base, token).callService(
                                    "input_text", "set_value", entityId,
                                    extraData = mapOf("value" to text),
                                )
                                EntityRepository.refresh(applicationContext, entityId)
                            }
                            busy = false
                            finish()
                        }
                    }

                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = text,
                            onValueChange = { if (it.length <= maxLength) text = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            OutlinedButton(onClick = { finish() }, enabled = !busy) { Text("Annullér") }
                            Spacer(Modifier.padding(4.dp))
                            Button(onClick = { save() }, enabled = !busy) { Text("Gem") }
                        }
                    }
                }
            }
        }
    }
}

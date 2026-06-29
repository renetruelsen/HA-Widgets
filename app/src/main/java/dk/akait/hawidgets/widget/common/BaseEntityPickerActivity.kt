package dk.akait.hawidgets.widget.common

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dk.akait.hawidgets.data.HaApiClient
import dk.akait.hawidgets.data.SecureStore
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.data.db.EntityWidgetEntity
import dk.akait.hawidgets.worker.SyncWorker
import kotlinx.coroutines.launch

abstract class BaseEntityPickerActivity : ComponentActivity() {

    abstract val domain: String
    abstract val pickerTitle: String
    abstract val domainIconResId: Int

    /** Translate state string to display label in entity list. Default: raw state. */
    open fun formatEntityState(state: String): String = state

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        setContent {
            MaterialTheme {
                EntityPickerScreen(
                    appWidgetId = appWidgetId,
                    domain = domain,
                    title = pickerTitle,
                    iconResId = domainIconResId,
                    formatState = ::formatEntityState,
                    onEntitySelected = { brief, label -> saveAndFinish(brief, label) },
                )
            }
        }
    }

    private fun saveAndFinish(brief: HaApiClient.EntityBrief, label: String) {
        val app = applicationContext
        val id = appWidgetId
        lifecycleScope.launch {
            AppDatabase.get(app).entityWidgetDao().upsert(
                EntityWidgetEntity(appWidgetId = id, entityId = brief.entityId, domain = domain, label = label.trim())
            )
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            SyncWorker.runNow(app)
            SyncWorker.schedule(app)
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntityPickerScreen(
    appWidgetId: Int,
    domain: String,
    title: String,
    iconResId: Int,
    formatState: (String) -> String,
    onEntitySelected: (HaApiClient.EntityBrief, String) -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }
    var entities by remember { mutableStateOf<List<HaApiClient.EntityBrief>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedEntity by remember { mutableStateOf<HaApiClient.EntityBrief?>(null) }
    var labelInput by remember { mutableStateOf("") }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val store = SecureStore.get(context)
        if (!store.isConfigured) {
            error = "HA ikke forbundet. Åbn HA Widgets og forbind først."
            isLoading = false
            return@LaunchedEffect
        }
        val existingCfg = AppDatabase.get(context).entityWidgetDao().get(appWidgetId)
        val client = HaApiClient(store.baseUrl!!, store.token!!)
        entities = client.listStatesByDomain(domain).sortedBy { it.friendlyName }
        isLoading = false
        if (existingCfg != null) {
            labelInput = existingCfg.label
            selectedEntity = entities.find { it.entityId == existingCfg.entityId }
        }
    }

    if (selectedEntity != null) {
        Scaffold(topBar = { TopAppBar(title = { Text("Tilpas widget") }) }) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            ) {
                Text(
                    selectedEntity!!.friendlyName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    selectedEntity!!.entityId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(8.dp))
                OutlinedTextField(
                    value = labelInput,
                    onValueChange = { if (it.length <= 12) labelInput = it },
                    label = { Text("Kort label (valgfrit)") },
                    placeholder = { Text("f.eks. Bad 1") },
                    supportingText = { Text("Vises på widget i stedet for enhedsnavn. Maks 12 tegn.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.padding(8.dp))
                Button(
                    onClick = { onEntitySelected(selectedEntity!!, labelInput) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Gem widget") }
                TextButton(
                    onClick = { selectedEntity = null; labelInput = "" },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Tilbage") }
            }
        }
        return
    }

    Scaffold(topBar = { TopAppBar(title = { Text(title) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Søg…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
            )
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Box(
                    Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    val filtered = entities.filter {
                        searchQuery.isBlank() ||
                            it.friendlyName.contains(searchQuery, ignoreCase = true) ||
                            it.entityId.contains(searchQuery, ignoreCase = true)
                    }
                    LazyColumn {
                        items(filtered) { brief ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedEntity = brief }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painter = painterResource(iconResId),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (brief.state == "on") MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        brief.friendlyName,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        brief.entityId,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(formatState(brief.state)) },
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

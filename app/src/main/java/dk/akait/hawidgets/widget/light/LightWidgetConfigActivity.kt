package dk.akait.hawidgets.widget.light

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dk.akait.hawidgets.R
import dk.akait.hawidgets.data.HaApiClient
import dk.akait.hawidgets.data.SecureStore
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.data.db.EntityWidgetEntity
import dk.akait.hawidgets.worker.SyncWorker
import kotlinx.coroutines.launch

class LightWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                val scope = rememberCoroutineScope()

                LightPickerScreen(
                    onEntitySelected = { brief ->
                        scope.launch {
                            val db = AppDatabase.get(this@LightWidgetConfigActivity)
                            db.entityWidgetDao().upsert(
                                EntityWidgetEntity(
                                    appWidgetId = appWidgetId,
                                    entityId = brief.entityId,
                                    domain = "light",
                                    label = "",
                                )
                            )
                            SyncWorker.schedule(this@LightWidgetConfigActivity)
                            SyncWorker.runNow(this@LightWidgetConfigActivity)

                            val result = Intent().apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            }
                            setResult(RESULT_OK, result)
                            finish()
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LightPickerScreen(onEntitySelected: (HaApiClient.EntityBrief) -> Unit) {
    var isLoading by remember { mutableStateOf(true) }
    var entities by remember { mutableStateOf<List<HaApiClient.EntityBrief>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        val store = SecureStore.get(context)
        if (!store.isConfigured) {
            error = "HA ikke forbundet. Åbn HA Widgets og forbind først."
            isLoading = false
            return@LaunchedEffect
        }
        val client = HaApiClient(store.baseUrl!!, store.token!!)
        entities = client.listStatesByDomain("light")
            .sortedBy { it.friendlyName }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Vælg lyskilde") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                error != null -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    val filtered = if (searchQuery.isBlank()) entities
                    else entities.filter {
                        it.friendlyName.contains(searchQuery, ignoreCase = true) ||
                            it.entityId.contains(searchQuery, ignoreCase = true)
                    }
                    LazyColumn {
                        items(filtered) { brief ->
                            EntityRow(brief = brief, onClick = { onEntitySelected(brief) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntityRow(brief: HaApiClient.EntityBrief, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lightbulb),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (brief.state == "on")
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(brief.friendlyName, style = MaterialTheme.typography.bodyLarge)
            Text(
                brief.entityId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SuggestionChip(
            onClick = {},
            label = { Text(if (brief.state == "on") "Tændt" else brief.state) },
        )
    }
}

package dk.akait.hawidgets.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import dk.akait.hawidgets.ui.theme.HaWidgetsTheme
import dk.akait.hawidgets.BuildConfig
import dk.akait.hawidgets.R
import dk.akait.hawidgets.data.DashboardInfo
import dk.akait.hawidgets.data.DisplayMode
import dk.akait.hawidgets.data.HaApiClient
import dk.akait.hawidgets.data.HaWebSocketClient
import dk.akait.hawidgets.data.SecureStore
import dk.akait.hawidgets.data.WidgetConfig
import dk.akait.hawidgets.data.WidgetConfigStore
import kotlinx.coroutines.launch

class ShortcutWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            HaWidgetsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ConfigScreen(
                        appWidgetId = appWidgetId,
                        onSave = ::saveAndFinish,
                        onCancel = ::finish,
                    )
                }
            }
        }
    }

    private fun saveAndFinish(config: WidgetConfig) {
        WidgetConfigStore.get(this).save(appWidgetId, config)
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        // Trigger a Glance update so the widget title reflects the new config. On API ≥31
        // the system also calls onUpdate automatically after the configure activity finishes.
        lifecycleScope.launch {
            runCatching {
                val glanceId = GlanceAppWidgetManager(this@ShortcutWidgetConfigActivity)
                    .getGlanceIdBy(appWidgetId)
                ShortcutWidget().update(this@ShortcutWidgetConfigActivity, glanceId)
            }
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(
    appWidgetId: Int,
    onSave: (WidgetConfig) -> Unit,
    onCancel: () -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { SecureStore.get(context) }
    val existingConfig = remember {
        WidgetConfigStore.get(context).get(appWidgetId)
    }

    // Step 1: HA connection (skipped if already configured)
    var haConfigured by remember { mutableStateOf(store.isConfigured) }
    var haUrl by remember {
        mutableStateOf(store.baseUrl ?: if (BuildConfig.DEBUG) BuildConfig.DEV_URL else "http://homeassistant.local:8123")
    }
    var haToken by remember {
        mutableStateOf(store.token ?: if (BuildConfig.DEBUG) BuildConfig.DEV_TOKEN else "")
    }
    var connecting by remember { mutableStateOf(false) }
    var connectError by remember { mutableStateOf<String?>(null) }
    val connectScope = rememberCoroutineScope()

    // Step 2: Widget config — initialise from existing config when reconfiguring
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var dashboards by remember { mutableStateOf<List<DashboardInfo>>(emptyList()) }
    var selected by remember { mutableStateOf<DashboardInfo?>(null) }
    var dashMenuOpen by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(existingConfig?.displayMode ?: DisplayMode.FULLSCREEN) }
    var widthPct by remember { mutableStateOf((existingConfig?.widthPct ?: 90).toFloat()) }
    var heightPct by remember { mutableStateOf((existingConfig?.heightPct ?: 80).toFloat()) }

    // Load dashboards once HA is configured; restore previously selected dashboard if reconfiguring
    androidx.compose.runtime.LaunchedEffect(haConfigured) {
        if (!haConfigured) return@LaunchedEffect
        loading = true
        HaWebSocketClient(store.baseUrl!!, store.token!!).listDashboards()
            .onSuccess { list ->
                dashboards = list
                selected = if (existingConfig != null) {
                    list.firstOrNull { it.urlPath == existingConfig.dashboardPath } ?: list.firstOrNull()
                } else {
                    list.firstOrNull()
                }
            }
            .onFailure { loadError = context.getString(R.string.load_dashboards_error, it.message ?: "") }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.configure_widget_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                }
            )
        },
        bottomBar = {
            if (haConfigured && !loading && loadError == null) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .navigationBarsPadding()
                ) {
                    Button(
                        enabled = selected != null,
                        onClick = {
                            val d = selected!!
                            onSave(
                                WidgetConfig(
                                    dashboardPath = d.urlPath,
                                    title = d.title,
                                    displayMode = mode,
                                    widthPct = widthPct.toInt(),
                                    heightPct = heightPct.toInt(),
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.save_widget)) }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!haConfigured) {
                // ── Step 1: Connect to HA ───────────────────────────────────
                Text(stringResource(R.string.connect_to_ha_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.connect_to_ha_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = haUrl,
                    onValueChange = { haUrl = it; connectError = null },
                    label = { Text(stringResource(R.string.ha_url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = haToken,
                    onValueChange = { haToken = it; connectError = null },
                    label = { Text(stringResource(R.string.token_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                connectError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    enabled = !connecting && haUrl.isNotBlank() && haToken.isNotBlank(),
                    onClick = {
                        connecting = true
                        connectError = null
                        val url = haUrl.trim()
                        val tok = haToken.trim()
                        connectScope.launch {
                            when (val r = HaApiClient(url, tok).checkConnection()) {
                                is HaApiClient.Result.Ok -> {
                                    store.baseUrl = url
                                    store.token = tok
                                    haConfigured = true
                                }
                                is HaApiClient.Result.Error -> connectError = r.message
                            }
                            connecting = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (connecting) stringResource(R.string.connecting) else stringResource(R.string.connect)) }
            } else {
                // ── Step 2: Select dashboard and settings ───────────────────
                when {
                    loading -> CircularProgressIndicator()
                    loadError != null -> Text(loadError!!, color = MaterialTheme.colorScheme.error)
                    else -> {
                        // Dashboard dropdown
                        SectionLabel(stringResource(R.string.section_dashboard))
                        ExposedDropdownMenuBox(
                            expanded = dashMenuOpen,
                            onExpandedChange = { dashMenuOpen = it }
                        ) {
                            OutlinedTextField(
                                value = selected?.title ?: stringResource(R.string.select_dashboard),
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dashMenuOpen) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = dashMenuOpen,
                                onDismissRequest = { dashMenuOpen = false }
                            ) {
                                dashboards.forEach { d ->
                                    DropdownMenuItem(
                                        text = { Text(d.title) },
                                        onClick = { selected = d; dashMenuOpen = false }
                                    )
                                }
                            }
                        }

                        // Display mode
                        SectionLabel(stringResource(R.string.section_display))
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            OptionRow(stringResource(R.string.display_fullscreen), mode == DisplayMode.FULLSCREEN) { mode = DisplayMode.FULLSCREEN }
                            OptionRow(stringResource(R.string.display_overlay), mode == DisplayMode.OVERLAY) { mode = DisplayMode.OVERLAY }
                        }

                        if (mode == DisplayMode.OVERLAY) {
                            Text(stringResource(R.string.overlay_width, widthPct.toInt()), style = MaterialTheme.typography.bodySmall)
                            Slider(value = widthPct, onValueChange = { widthPct = it }, valueRange = 40f..100f)
                            Text(stringResource(R.string.overlay_height, heightPct.toInt()), style = MaterialTheme.typography.bodySmall)
                            Slider(value = heightPct, onValueChange = { heightPct = it }, valueRange = 40f..100f)
                        }


                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

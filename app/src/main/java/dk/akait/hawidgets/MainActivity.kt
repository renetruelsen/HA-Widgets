package dk.akait.hawidgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.glance.appwidget.updateAll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dk.akait.hawidgets.data.HaApiClient
import dk.akait.hawidgets.data.SecureStore
import dk.akait.hawidgets.ui.theme.HaWidgetsTheme
import dk.akait.hawidgets.widget.ShortcutWidget
import dk.akait.hawidgets.widget.ShortcutWidgetReceiver
import dk.akait.hawidgets.widget.multientity.MultiEntityWidget
import dk.akait.hawidgets.widget.common.WIDGET_COLOR_THEMES
import dk.akait.hawidgets.widget.common.presetFor
import dk.akait.hawidgets.logging.RemoteLogger
import dk.akait.hawidgets.logging.ReportProblemDialog
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material.icons.filled.BugReport
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN_SETTINGS = "open_settings"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val openSettings = intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)
        setContent {
            HaWidgetsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OnboardingScreen(openSettingsInitially = openSettings)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingScreen(openSettingsInitially: Boolean = false) {
    val context = LocalContext.current
    val store = remember { SecureStore.get(context) }
    val scope = rememberCoroutineScope()

    var baseUrl by remember { mutableStateOf(store.baseUrl ?: "http://homeassistant.local:8123") }
    var token by remember { mutableStateOf(store.token ?: "") }
    var connected by remember { mutableStateOf(store.isConfigured) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showBatteryDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(openSettingsInitially) }
    var showTokenHelp by remember { mutableStateOf(false) }
    var reportCrashSummary by remember { mutableStateOf(store.pendingCrashSummary) }
    var showReportDialog by remember { mutableStateOf(reportCrashSummary != null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val pm = remember { context.getSystemService(PowerManager::class.java) }
    LaunchedEffect(connected) {
        if (connected && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
            showBatteryDialog = true
        }
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text(stringResource(R.string.disconnect_dialog_title)) },
            text = { Text(stringResource(R.string.disconnect_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.clearConnection()
                        connected = false
                        token = ""
                        status = ""
                        showDisconnectDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.disconnect)) }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryDialog = false },
            title = { Text(stringResource(R.string.battery_dialog_title)) },
            text = { Text(stringResource(R.string.battery_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showBatteryDialog = false
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }) { Text(stringResource(R.string.battery_dialog_allow)) }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryDialog = false }) {
                    Text(stringResource(R.string.battery_dialog_later))
                }
            }
        )
    }

    if (showTokenHelp) {
        AlertDialog(
            onDismissRequest = { showTokenHelp = false },
            title = { Text(stringResource(R.string.token_help_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StepRow(1, stringResource(R.string.token_help_step1))
                    StepRow(2, stringResource(R.string.token_help_step2))
                    StepRow(3, stringResource(R.string.token_help_step3))
                    StepRow(4, stringResource(R.string.token_help_step4))
                }
            },
            confirmButton = {
                TextButton(onClick = { showTokenHelp = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    if (showSettings) {
        SettingsSheet(
            context = context,
            pm = pm,
            connected = connected,
            onDismiss = { showSettings = false },
            onReportProblem = {
                showSettings = false
                reportCrashSummary = null
                showReportDialog = true
            }
        )
    }

    if (showReportDialog) {
        ReportProblemDialog(
            crashSummary = reportCrashSummary,
            onDismiss = {
                showReportDialog = false
                if (reportCrashSummary != null) {
                    store.clearPendingCrash()
                    reportCrashSummary = null
                }
            },
            onResult = { result ->
                scope.launch {
                    val message = reportResultMessage(context, result)
                    if (result is RemoteLogger.UploadResult.NetworkError) {
                        val action = snackbarHostState.showSnackbar(
                            message = message,
                            actionLabel = context.getString(R.string.retry_action),
                            duration = SnackbarDuration.Long
                        )
                        if (action == SnackbarResult.ActionPerformed) {
                            showReportDialog = true
                        }
                    } else {
                        snackbarHostState.showSnackbar(message)
                    }
                }
            }
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (connected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.section_settings_title))
                    }
                }

                SectionLabel(Icons.Default.Link, stringResource(R.string.section_connection))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                stringResource(R.string.connected_to),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            store.baseUrl ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(
                                onClick = { showDisconnectDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                            ) { Text(stringResource(R.string.disconnect)) }
                        }
                    }
                }

                SectionLabel(Icons.Default.RocketLaunch, stringResource(R.string.section_getting_started))

                val pinSupported = remember {
                    AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
                }
                Button(
                    onClick = {
                        val awm = AppWidgetManager.getInstance(context)
                        val provider = ComponentName(context, ShortcutWidgetReceiver::class.java)
                        if (awm.isRequestPinAppWidgetSupported) {
                            awm.requestPinAppWidget(provider, null, null)
                            (context as? android.app.Activity)?.moveTaskToBack(true)
                        }
                    },
                    enabled = pinSupported,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.add_widget_to_home)) }

                Text(
                    if (!pinSupported) stringResource(R.string.pin_manual_instructions)
                    else stringResource(R.string.pin_button_instructions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.how_to_use_title), style = MaterialTheme.typography.labelLarge)
                        StepRow(1, stringResource(R.string.how_to_use_step1))
                        StepRow(2, stringResource(R.string.how_to_use_step2))
                        StepRow(3, stringResource(R.string.how_to_use_step3))
                    }
                }
            } else {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)

                Text(
                    stringResource(R.string.onboarding_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(R.string.ha_url_label)) },
                    placeholder = { Text("http://homeassistant.local:8123") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.token_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                TextButton(onClick = { showTokenHelp = true }) {
                    Text(stringResource(R.string.token_help_link))
                }

                Button(
                    enabled = !busy && baseUrl.isNotBlank() && token.isNotBlank(),
                    onClick = {
                        busy = true
                        status = context.getString(R.string.checking_connection)
                        val url = baseUrl.trim()
                        val tok = token.trim()
                        scope.launch {
                            when (val r = HaApiClient(url, tok).checkConnection()) {
                                is HaApiClient.Result.Ok -> {
                                    store.baseUrl = url
                                    store.token = tok
                                    connected = true
                                    status = ""
                                }
                                is HaApiClient.Result.Error -> status = r.message
                            }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) stringResource(R.string.connecting) else stringResource(R.string.connect)) }

                if (status.isNotBlank()) {
                    Text(status, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun StepRow(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    context: android.content.Context,
    pm: PowerManager,
    connected: Boolean,
    onDismiss: () -> Unit,
    onReportProblem: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.section_settings_title), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close_settings))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionHeader(stringResource(R.string.section_appearance))

            val store = remember { SecureStore.get(context) }
            var themeMode by remember { mutableStateOf(store.themeMode) }
            ThemeRow(currentMode = themeMode) { mode ->
                store.themeMode = mode
                themeMode = mode
                // Widgets læser IKKE Room for tema-valget (det bor i SecureStore), så en
                // reaktiv Flow kan ikke drive gen-render. Tving derfor hver placeret widget
                // til at gen-tegne via updateAll() (ADR-5).
                updateAllWidgets(context)
                // App-UI'et gen-læser themeMode i HaWidgetsTheme ved recomposition —
                // recreate() sikrer at HELE activity-træet (inkl. denne sheet) skifter tema.
                (context as? android.app.Activity)?.recreate()
            }

            var colorTheme by remember { mutableStateOf(store.widgetColorTheme) }
            ColorThemeRow(currentTheme = colorTheme) { theme ->
                store.widgetColorTheme = theme
                colorTheme = theme
                // Samme begrundelse som ThemeRow/LanguageRow (ADR-5): widgets observerer ikke
                // SecureStore reaktivt, så en eksplicit updateAll() er nødvendig. Ingen recreate()
                // her — farvetemaet påvirker KUN widgets, ikke app-UI'et (jf. spec).
                updateAllWidgets(context)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                var currentTag by remember { mutableStateOf(currentLanguageTag(context)) }
                LanguageRow(currentTag = currentTag) { tag ->
                    setAppLocale(context, tag)
                    currentTag = tag
                    // Widgets gen-læser ikke locale reaktivt (samme begrundelse som tema, ADR-5) —
                    // uden dette ville placerede widgets først skifte sprog ved næste periodiske sync.
                    updateAllWidgets(context)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionHeader(stringResource(R.string.section_troubleshooting))

            val batteryExempted = remember(connected) {
                pm.isIgnoringBatteryOptimizations(context.packageName)
            }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.BatteryFull, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.battery_manage), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (batteryExempted) stringResource(R.string.battery_status_exempt)
                        else stringResource(R.string.battery_status_restricted),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (batteryExempted) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }) { Text(stringResource(R.string.settings_open)) }
            }

            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.report_problem_row_title), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.report_problem_row_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onReportProblem) { Text(stringResource(R.string.report_problem_button)) }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun currentLanguageTag(context: android.content.Context): String? {
    val localeManager = context.getSystemService(LocaleManager::class.java)
    val tag = localeManager.applicationLocales.toLanguageTags()
    return tag.takeIf { it.isNotBlank() }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun setAppLocale(context: android.content.Context, languageTag: String?) {
    val localeManager = context.getSystemService(LocaleManager::class.java)
    localeManager.applicationLocales =
        if (languageTag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(languageTag)
}

/**
 * Delt indstillings-dropdown-række (ikon + label + valgt-værdi + menu). Bruges af [ThemeRow],
 * [LanguageRow] og [ColorThemeRow] — al præsentation/ekspansion bor ét sted, så padding/typografi/
 * a11y ikke kan drive fra hinanden. Nøgletypen [T] er generisk (sprog-rækken bruger `String?`).
 * [leadingIcon] tegner en valgfri prik/ikon foran hvert menupunkt (kun farvetema bruger det).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SettingsDropdownRow(
    icon: ImageVector,
    label: String,
    options: List<Pair<T, String>>,
    selectedKey: T,
    fallbackLabel: String,
    onSelect: (T) -> Unit,
    leadingIcon: (@Composable (T) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedKey }?.second ?: fallbackLabel

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                selectedLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = if (leadingIcon != null) {
                        { leadingIcon(key) }
                    } else null,
                    onClick = {
                        onSelect(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun LanguageRow(currentTag: String?, onSelect: (String?) -> Unit) {
    SettingsDropdownRow(
        icon = Icons.Default.Language,
        label = stringResource(R.string.section_language),
        options = listOf(
            null to stringResource(R.string.language_follow_system),
            "da" to stringResource(R.string.language_danish),
            "en" to stringResource(R.string.language_english),
            "sv" to stringResource(R.string.language_swedish),
        ),
        selectedKey = currentTag,
        fallbackLabel = stringResource(R.string.language_follow_system),
        onSelect = onSelect,
    )
}

@Composable
private fun ThemeRow(currentMode: String, onSelect: (String) -> Unit) {
    SettingsDropdownRow(
        icon = Icons.Default.Palette,
        label = stringResource(R.string.theme_label),
        options = listOf(
            SecureStore.THEME_SYSTEM to stringResource(R.string.theme_system),
            SecureStore.THEME_LIGHT to stringResource(R.string.theme_light),
            SecureStore.THEME_DARK to stringResource(R.string.theme_dark),
        ),
        selectedKey = currentMode,
        fallbackLabel = stringResource(R.string.theme_system),
        onSelect = onSelect,
    )
}

@Composable
private fun ColorThemeRow(currentTheme: String, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    // Swatch-prikken skal vise DEN farve widgetten faktisk render­er i det aktive tema — så beregnes
    // dark/light samme sted som HaWidgetsTheme/WidgetColors gør (themeMode → dark? ellers OS-natvalg).
    val dark = when (SecureStore.get(context).themeMode) {
        SecureStore.THEME_DARK -> true
        SecureStore.THEME_LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    SettingsDropdownRow(
        icon = Icons.Default.ColorLens,
        label = stringResource(R.string.widget_color_theme_label),
        options = WIDGET_COLOR_THEMES.map { it.key to stringResource(it.labelRes) },
        selectedKey = currentTheme,
        fallbackLabel = stringResource(R.string.color_theme_blue),
        onSelect = onSelect,
        leadingIcon = { key ->
            val preset = presetFor(key)
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (dark) preset.dark.primary else preset.light.primary)
            )
        },
    )
}

/** Tving alle placerede Glance-widgets til at gen-tegne efter et tema-/farve-/sprog-skift. Én linje
 * pr. GlanceAppWidget-klasse (jf. AndroidManifest's <receiver>-liste). Kører på [HaWidgetsApp.appScope]
 * — IKKE en composable-scope — så arbejdet overlever det `recreate()` et tema-skift udløser (ellers
 * blev opdateringen annulleret midt i, og kun nogle widgets nåede at gen-tegne). */
private fun updateAllWidgets(context: android.content.Context) {
    val app = context.applicationContext as HaWidgetsApp
    app.appScope.launch {
        MultiEntityWidget().updateAll(app)
        ShortcutWidget().updateAll(app)
    }
}

/** Oversætter et [RemoteLogger.UploadResult] til den lokaliserede besked vist i Snackbaren efter
 * "Report a problem". [RemoteLogger.UploadResult.Throttled] optræder aldrig herfra i praksis
 * (rapport-dialogen sender altid med `force = true`) — mappet til samme tekst som netværksfejl
 * som et harmløst fallback for en udtømmende `when`. */
private fun reportResultMessage(context: android.content.Context, result: RemoteLogger.UploadResult): String =
    when (result) {
        is RemoteLogger.UploadResult.Success -> context.getString(R.string.report_problem_success)
        is RemoteLogger.UploadResult.NotConfigured -> context.getString(R.string.report_problem_not_configured)
        is RemoteLogger.UploadResult.NetworkError -> context.getString(R.string.report_problem_network_error)
        is RemoteLogger.UploadResult.Throttled -> context.getString(R.string.report_problem_network_error)
        is RemoteLogger.UploadResult.ServerRejected -> context.getString(R.string.report_problem_server_rejected)
    }

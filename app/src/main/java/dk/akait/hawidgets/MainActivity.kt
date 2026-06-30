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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dk.akait.hawidgets.data.HaApiClient
import dk.akait.hawidgets.data.SecureStore
import dk.akait.hawidgets.widget.ShortcutWidgetReceiver
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OnboardingScreen()
                }
            }
        }
    }
}

@Composable
private fun OnboardingScreen() {
    val context = LocalContext.current
    val store = remember { SecureStore.get(context) }
    val scope = rememberCoroutineScope()

    var baseUrl by remember { mutableStateOf(store.baseUrl ?: if (BuildConfig.DEBUG) BuildConfig.DEV_URL else "http://homeassistant.local:8123") }
    var token by remember { mutableStateOf(store.token ?: if (BuildConfig.DEBUG) BuildConfig.DEV_TOKEN else "") }
    var connected by remember { mutableStateOf(store.isConfigured) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showBatteryDialog by remember { mutableStateOf(false) }

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

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)

            if (connected) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.connected_to), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        Text(store.baseUrl ?: "", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                Spacer(Modifier.height(8.dp))

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

                Spacer(Modifier.height(8.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.how_to_use_title), style = MaterialTheme.typography.labelLarge)
                        Text(stringResource(R.string.how_to_use_step1), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.how_to_use_step2), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.how_to_use_step3), style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(Modifier.height(8.dp))

                val batteryExempted = remember(connected) {
                    pm.isIgnoringBatteryOptimizations(context.packageName)
                }
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.battery_manage)) }
                Text(
                    if (batteryExempted) stringResource(R.string.battery_status_exempt)
                    else stringResource(R.string.battery_status_restricted),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (batteryExempted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    var currentTag by remember { mutableStateOf(currentLanguageTag(context)) }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.section_language), style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LanguageOption(
                                    label = stringResource(R.string.language_danish),
                                    selected = currentTag == "da"
                                ) {
                                    setAppLocale(context, "da")
                                    currentTag = "da"
                                }
                                LanguageOption(
                                    label = stringResource(R.string.language_english),
                                    selected = currentTag == "en"
                                ) {
                                    setAppLocale(context, "en")
                                    currentTag = "en"
                                }
                                LanguageOption(
                                    label = stringResource(R.string.language_swedish),
                                    selected = currentTag == "sv"
                                ) {
                                    setAppLocale(context, "sv")
                                    currentTag = "sv"
                                }
                            }
                            LanguageOption(
                                label = stringResource(R.string.language_follow_system),
                                selected = currentTag == null,
                                fillWidth = true
                            ) {
                                setAppLocale(context, null)
                                currentTag = null
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }

                OutlinedButton(
                    onClick = { showDisconnectDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.disconnect)) }
            } else {
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

@Composable
private fun LanguageOption(
    label: String,
    selected: Boolean,
    fillWidth: Boolean = false,
    onClick: () -> Unit
) {
    val modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

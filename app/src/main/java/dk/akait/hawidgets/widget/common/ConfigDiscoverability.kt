package dk.akait.hawidgets.widget.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dk.akait.hawidgets.R

/**
 * "Ikke forbundet"-gate til widget-config-skærmene. Vises når [dk.akait.hawidgets.data.SecureStore]
 * ikke er konfigureret — leder brugeren til hoved-appen, hvor AL global opsætning (forbindelse, sprog,
 * tema, farver) bor. [onOpenApp] skal starte MainActivity.
 */
@Composable
fun NotConnectedGate(onOpenApp: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.WifiOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.not_connected_gate_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.not_connected_gate_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onOpenApp, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.OpenInNew, contentDescription = null)
            Text(
                stringResource(R.string.open_app_button),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * Diskret bund-henvisning: minder om at sprog/tema/farver ligger i appen. [onOpenSettings] skal
 * starte MainActivity med indstillings-arket åbent (deep-link).
 */
@Composable
fun AppSettingsHint(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenSettings() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Default.Settings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.settings_in_app_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.open_short),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Tæller der stiger hver gang den omgivende aktivitet får `ON_RESUME`. Bruges som `LaunchedEffect`-key
 * i config-skærmene, så de gen-tjekker forbindelses-status når brugeren vender tilbage fra appen (efter
 * at have forbundet dér) — uden at skulle fjerne+gen-tilføje widgetten.
 */
@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return tick
}

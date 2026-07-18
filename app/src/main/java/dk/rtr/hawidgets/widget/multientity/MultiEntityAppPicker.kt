package dk.rtr.hawidgets.widget.multientity

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dk.rtr.hawidgets.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Én installeret app der kan startes (har en launcher-aktivitet). */
internal data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
)

/** Alle apps med en launcher-genvej (dem der har et ikon i app-skuffen). Kræver `<queries>` for
 * MAIN/LAUNCHER i manifestet på Android 11+ — undgår den Play-restriktede QUERY_ALL_PACKAGES. */
internal suspend fun loadLaunchableApps(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(intent, 0)
        .map { ri ->
            InstalledApp(
                packageName = ri.activityInfo.packageName,
                label = ri.loadLabel(pm).toString(),
                icon = ri.loadIcon(pm).toBitmap().asImageBitmap(),
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppPickerScreen(
    onSelected: (packageName: String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val apps by produceState(initialValue = emptyList<InstalledApp>(), context) {
        value = loadLaunchableApps(context)
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.choose_app)) },
            navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
        )
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
            )
            if (apps.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val filtered = apps.filter {
                    query.isBlank() ||
                        it.label.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
                }
                LazyColumn {
                    items(filtered) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelected(app.packageName) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                bitmap = app.icon,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

package dk.akait.hawidgets.transfer

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dk.akait.hawidgets.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Overflow-menu (⋮) i config-skærmenes TopAppBar: "Eksportér denne widget" + "Importér konfiguration",
 * plus "Gendan fjernet widget" NÅR [onRecover] er sat (kun multi-config, og kun hvis der findes
 * soft-slettede configs inden for grace-perioden).
 */
@Composable
fun TransferOverflowMenu(onExport: () -> Unit, onImport: () -> Unit, onRecover: (() -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.export_this_widget)) },
            onClick = { open = false; onExport() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.import_config)) },
            onClick = { open = false; onImport() },
        )
        if (onRecover != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.recover_removed_widget)) },
                onClick = { open = false; onRecover() },
            )
        }
    }
}

/** Én post i import-vælgeren — auto-udledt visning af en widget-config i filen. */
data class ImportPickerItem(val title: String, val subtitle: String, @DrawableRes val iconResId: Int)

/** Vælger over en liste af widget-configs (fil-import ELLER gendan-fjernet) — altid vist, også ved én. */
@Composable
fun ImportPickerDialog(
    items: List<ImportPickerItem>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    titleRes: Int = R.string.import_config,
    countRes: Int = R.plurals.import_count,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(titleRes))
                Text(
                    pluralStringResource(countRes, items.size, items.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column {
                items.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { onPick(index) }
                            .padding(vertical = 8.dp),
                    ) {
                        Icon(
                            painter = painterResource(item.iconResId),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                item.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Bekræft-dialog før en importeret config overskriver den nuværende opsætning. */
@Composable
fun ConfirmReplaceDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_confirm_title)) },
        text = { Text(stringResource(R.string.import_confirm_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.import_replace_button)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * SAF-baseret import-launcher: brugeren vælger en fil, den læses + parses, og [onBundle] kaldes
 * ved succes. Parse-/læsefejl vises som Toast direkte. Kald `.launch(TRANSFER_IMPORT_MIME_TYPES)`.
 */
@Composable
fun rememberImportLauncher(onBundle: (TransferBundle) -> Unit): ManagedActivityResultLauncher<Array<String>, Uri?> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Læsning + parse på IO — en SAF-uri kan pege på en cloud-provider (Drive), hvis
        // openInputStream blokerer mens filen hentes; UI-callbacken kører ellers på main-tråden.
        // Resultatet leveres tilbage på Main (scope er Main), hvor onBundle opdaterer Compose-state.
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val raw = WidgetTransferIo.readDocument(context, uri)
                    ?: return@withContext Result.failure<TransferBundle>(ImportException(ImportError.InvalidJson))
                parseTransferBundle(raw)
            }
            result.fold(
                onSuccess = { onBundle(it) },
                onFailure = { e ->
                    val error = (e as? ImportException)?.error ?: ImportError.InvalidJson
                    Toast.makeText(context, importErrorMessage(context, error), Toast.LENGTH_LONG).show()
                },
            )
        }
    }
}

/** Bredt MIME-filter — nogle filhåndteringer rapporterer .json som octet-stream/text. */
val TRANSFER_IMPORT_MIME_TYPES = arrayOf("application/json", "text/*", "application/octet-stream")

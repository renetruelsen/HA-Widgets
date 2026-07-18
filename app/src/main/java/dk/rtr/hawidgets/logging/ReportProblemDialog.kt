package dk.rtr.hawidgets.logging

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dk.rtr.hawidgets.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val MAX_NOTE_LENGTH = 300
private const val SEND_TIMEOUT_MS = 15_000L

/**
 * Rig fejlrapport-dialog: note-felt (valgfrit, maks 300 tegn) + Copy log/Cancel/Send report,
 * efterfulgt af en ikke-lukkelig sender-dialog og et resultat i [RemoteLogger.UploadResult]-form
 * til [onResult] (controlleren viser en Snackbar). [crashSummary] null = almindelig menu-trigger;
 * ikke-null = automatisk åbnet efter et crash i forrige proces (viser en ekstra intro-linje).
 * Se docs/superpowers/specs/2026-07-13-settings-redesign-error-report-design.md.
 */
@Composable
fun ReportProblemDialog(
    crashSummary: String?,
    onDismiss: () -> Unit,
    onResult: (RemoteLogger.UploadResult) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    if (sending) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(bottom = 12.dp))
                    Text(stringResource(R.string.sending_in_progress))
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (crashSummary != null) stringResource(R.string.report_problem_crash_title)
                else stringResource(R.string.report_problem_title)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (crashSummary != null) stringResource(R.string.report_problem_crash_body, crashSummary)
                    else stringResource(R.string.report_problem_body)
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(MAX_NOTE_LENGTH) },
                    label = { Text(stringResource(R.string.report_problem_note_label)) },
                    supportingText = { Text("${note.length}/$MAX_NOTE_LENGTH") },
                    minLines = 3,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                sending = true
                scope.launch {
                    val result = sendReport(context, note)
                    sending = false
                    onDismiss()
                    onResult(result)
                }
            }) { Text(stringResource(R.string.report_problem_button_send)) }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { scope.launch { copyLogToClipboard(context) } }) {
                    Text(stringResource(R.string.copy_log))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

private suspend fun copyLogToClipboard(context: Context) {
    val lines = RemoteLogger.recentLines(30) + collectWidgetConfigDump(context.applicationContext)
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("ha-widgets log", lines.joinToString("\n")))
    Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
}

private suspend fun sendReport(context: Context, note: String): RemoteLogger.UploadResult {
    val configLines = buildList {
        if (note.isNotBlank()) add(formatLogLine('I', "USER_NOTE", note.trim()))
        addAll(collectWidgetConfigDump(context.applicationContext))
    }
    return withTimeoutOrNull(SEND_TIMEOUT_MS) {
        withContext(Dispatchers.IO) { RemoteLogger.flush(force = true, configLines = configLines) }
    } ?: RemoteLogger.UploadResult.NetworkError
}

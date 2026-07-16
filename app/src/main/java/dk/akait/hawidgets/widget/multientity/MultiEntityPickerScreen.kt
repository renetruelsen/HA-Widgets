package dk.akait.hawidgets.widget.multientity

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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dk.akait.hawidgets.R
import dk.akait.hawidgets.data.HaApiClient
import dk.akait.hawidgets.widget.common.domainIconResId
import dk.akait.hawidgets.widget.common.formatEntityState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntityPickerSubScreen(
    title: String,
    entities: List<HaApiClient.EntityBrief>,
    isLoading: Boolean,
    error: String?,
    onSelected: (HaApiClient.EntityBrief) -> Unit,
    onBack: () -> Unit,
    onSkip: (() -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
        )
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
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
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    val filtered = entities.filter {
                        query.isBlank() ||
                            it.friendlyName.contains(query, ignoreCase = true) ||
                            it.entityId.contains(query, ignoreCase = true)
                    }
                    LazyColumn {
                        if (onSkip != null) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSkip() }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        stringResource(R.string.skip_main_entity),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                        items(filtered) { brief ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelected(brief) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painter = painterResource(domainIconResId(brief.domain)),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (brief.state == "on") MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(brief.friendlyName, style = MaterialTheme.typography.bodyLarge)
                                    Text(brief.entityId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                SuggestionChip(onClick = {}, label = { Text(formatEntityState(context, brief.domain, brief.state, brief.unit)) })
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

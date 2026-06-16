/*
 * Copyright (c) 2026 Lunabee Studio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package studio.lunabee.compose.demo.synchronization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import studio.lunabee.synchronization.syncmanager.LBSyncProcessStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import java.time.Instant as JavaInstant

@Composable
fun SyncDemoScreen(
    viewModel: SyncDemoViewModel = viewModel(),
) {
    val localItems by viewModel.localItems.collectAsState()
    val serverItems by viewModel.serverItems.collectAsState()
    val status by viewModel.status.collectAsState()
    val failNextSync by viewModel.failNextSync.collectAsState()
    val conflicts by viewModel.conflicts.collectAsState()
    val showDeleted by viewModel.showDeleted.collectAsState()
    val refreshOnForeground by viewModel.refreshOnForeground.collectAsState()
    val refreshOnInternetBack by viewModel.refreshOnInternetBack.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val retryCount by viewModel.retryCount.collectAsState()

    val visibleLocalItems = if (showDeleted) localItems else localItems.filterNot { it.isDeleted }
    val visibleServerItems = if (showDeleted) serverItems else serverItems.filterNot { it.isDeleted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NetworkIndicator(isOnline = isOnline)

        StatusBanner(status = status)

        if (conflicts.isNotEmpty()) {
            ConflictBanner(conflicts = conflicts)
        }

        val errorAt = (status as? LBSyncProcessStatus.DownloadFinishWithError)?.at
            ?: (status as? LBSyncProcessStatus.UploadFinishWithError)?.at
        val retryTempo = viewModel.retryTempo
        if (errorAt != null && retryTempo != null) {
            RetryBanner(
                errorAt = errorAt,
                retryTempo = retryTempo,
                attempt = retryCount,
            )
        }

        ActionButtons(
            viewModel = viewModel,
            isProcessing = status.isProcessing(),
        )

        OptionsSection(
            failNextSync = failNextSync,
            onFailNextSync = viewModel::setFailNextSync,
            showDeleted = showDeleted,
            onShowDeleted = viewModel::setShowDeleted,
            refreshOnForeground = refreshOnForeground,
            onRefreshOnForeground = viewModel::setRefreshOnForeground,
            refreshOnInternetBack = refreshOnInternetBack,
            onRefreshOnInternetBack = viewModel::setRefreshOnInternetBack,
        )

        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClientColumn(
                items = visibleLocalItems,
                onTouch = viewModel::touchClientItem,
                onDelete = viewModel::deleteClientItem,
                onOverride = viewModel::overrideClientItemWithServer,
                modifier = Modifier.weight(1f),
            )
            ServerColumn(
                items = visibleServerItems,
                onTouch = viewModel::touchServerItem,
                onDelete = viewModel::deleteServerItem,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ActionButtons(
    viewModel: SyncDemoViewModel,
    isProcessing: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = viewModel::addClientItem,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = "+ Client item")
            }
            OutlinedButton(
                onClick = viewModel::addServerItem,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = "+ Server item")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = viewModel::synchronize,
                enabled = !isProcessing,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = "Synchronize")
            }
            OutlinedButton(
                onClick = viewModel::reset,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = "Reset")
            }
        }

        OutlinedButton(
            onClick = viewModel::clearLocalDb,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Clear client DB")
        }
    }
}

@Composable
private fun StatusBanner(
    status: LBSyncProcessStatus,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (status.isProcessing()) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 4.dp))
            }
            Column {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = status.fullDescription(),
                    style = MaterialTheme.typography.bodyLarge,
                )
                status.currentError()?.let { error ->
                    Text(
                        text = error.message.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun RetryBanner(
    errorAt: Instant,
    retryTempo: Duration,
    attempt: Int,
    modifier: Modifier = Modifier,
) {
    var remainingSeconds by remember(errorAt) {
        mutableLongStateOf(retryRemainingSeconds(errorAt, retryTempo))
    }
    LaunchedEffect(errorAt) {
        while (remainingSeconds > 0) {
            delay(1_000)
            remainingSeconds = retryRemainingSeconds(errorAt, retryTempo)
        }
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Sync failed — attempt #$attempt",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = if (remainingSeconds > 0) "Auto-retry in ${remainingSeconds}s" else "Retrying…",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun retryRemainingSeconds(errorAt: Instant, retryTempo: Duration): Long =
    (errorAt + retryTempo - Clock.System.now()).inWholeSeconds.coerceAtLeast(0)

@Composable
private fun NetworkIndicator(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (isOnline) "● Online" else "● Offline",
            color = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = if (isOnline) "server reachable" else "server unreachable — sync will fail",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun OptionsSection(
    failNextSync: Boolean,
    onFailNextSync: (Boolean) -> Unit,
    showDeleted: Boolean,
    onShowDeleted: (Boolean) -> Unit,
    refreshOnForeground: Boolean,
    onRefreshOnForeground: (Boolean) -> Unit,
    refreshOnInternetBack: Boolean,
    onRefreshOnInternetBack: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Options",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse options" else "Expand options",
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SwitchRow(
                    checked = failNextSync,
                    onCheckedChange = onFailNextSync,
                    label = "Make next sync fail (then auto-retry)",
                )
                SwitchRow(
                    checked = showDeleted,
                    onCheckedChange = onShowDeleted,
                    label = "Show deleted items",
                )
                SwitchRow(
                    checked = refreshOnForeground,
                    onCheckedChange = onRefreshOnForeground,
                    label = "Refresh on app foreground",
                )
                SwitchRow(
                    checked = refreshOnInternetBack,
                    onCheckedChange = onRefreshOnInternetBack,
                    label = "Refresh on internet back",
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(text = label)
    }
}

@Composable
private fun ConflictBanner(
    conflicts: List<String>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "⚠ ${conflicts.size} conflict(s) — edited on both sides",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = conflicts.joinToString(),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Other items synced fine. Touch a conflicted item to keep the local edit.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ClientColumn(
    items: List<LocalItem>,
    onTouch: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOverride: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Client DB (${items.size})",
            style = MaterialTheme.typography.titleSmall,
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(items = items, key = { it.id }) { item ->
                ItemCard(
                    label = item.label,
                    onTouch = { onTouch(item.id) },
                    onDelete = { onDelete(item.id) },
                    modifier = Modifier.animateItem(),
                    onOverride = { onOverride(item.id) },
                ) {
                    when {
                        item.isDeleted -> Text(
                            text = "🗑 deleted",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )

                        item.isConflicted -> Text(
                            text = "⚠ conflict — touch to keep local",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )

                        item.isSynced -> Text(
                            text = "✓ synced",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                        )

                        else -> Text(
                            text = "● pending upload",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text(
                        text = "updated ${formatUpdatedAt(item.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerColumn(
    items: List<ServerItem>,
    onTouch: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Fake server (${items.size})",
            style = MaterialTheme.typography.titleSmall,
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(items = items, key = { it.id }) { item ->
                ItemCard(
                    label = item.label,
                    onTouch = { onTouch(item.id) },
                    onDelete = { onDelete(item.id) },
                    modifier = Modifier.animateItem(),
                ) {
                    if (item.isDeleted) {
                        Text(
                            text = "🗑 deleted",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text(
                        text = "updated ${formatUpdatedAt(item.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

private val updatedAtFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

private fun formatUpdatedAt(instant: Instant): String =
    updatedAtFormatter.format(JavaInstant.ofEpochMilli(instant.toEpochMilliseconds()))

@Composable
private fun ItemCard(
    label: String,
    onTouch: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onOverride: (() -> Unit)? = null,
    subtitle: @Composable (() -> Unit)? = null,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                subtitle?.invoke()
            }
            ItemMenu(
                onTouch = onTouch,
                onDelete = onDelete,
                onOverride = onOverride,
            )
        }
    }
}

@Composable
private fun ItemMenu(
    onTouch: () -> Unit,
    onDelete: () -> Unit,
    onOverride: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "Item actions",
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        DropdownMenuItem(
            text = { Text(text = "Touch (set updatedAt to now)") },
            onClick = {
                expanded = false
                onTouch()
            },
        )
        if (onOverride != null) {
            DropdownMenuItem(
                text = { Text(text = "Override with server version") },
                onClick = {
                    expanded = false
                    onOverride()
                },
            )
        }
        DropdownMenuItem(
            text = { Text(text = "Delete") },
            onClick = {
                expanded = false
                onDelete()
            },
        )
    }
}

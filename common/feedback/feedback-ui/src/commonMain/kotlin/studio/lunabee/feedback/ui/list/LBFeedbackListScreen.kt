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

package studio.lunabee.feedback.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import studio.lunabee.feedback.core.model.LBFeedbackStatus
import studio.lunabee.feedback.core.model.LBFeedbackType
import studio.lunabee.feedback.ui.LBFeedback
import studio.lunabee.feedback.ui.LBFeedbackTestTags
import studio.lunabee.feedback.ui.error.feedbackErrorMessage
import studio.lunabee.feedback.ui.res.Res
import studio.lunabee.feedback.ui.res.feedback_list_delete
import studio.lunabee.feedback.ui.res.feedback_list_edit
import studio.lunabee.feedback.ui.res.feedback_list_empty
import studio.lunabee.feedback.ui.res.feedback_list_title
import studio.lunabee.feedback.ui.res.feedback_status_accepted
import studio.lunabee.feedback.ui.res.feedback_status_declined
import studio.lunabee.feedback.ui.res.feedback_status_duplicate
import studio.lunabee.feedback.ui.res.feedback_status_pending
import studio.lunabee.feedback.ui.res.feedback_status_snoozed
import studio.lunabee.feedback.ui.res.feedback_type_bug
import studio.lunabee.feedback.ui.res.feedback_type_idea
import studio.lunabee.feedback.ui.res.feedback_type_other
import studio.lunabee.feedback.ui.theme.LBFeedbackTopBar

/**
 * "My feedback": the items sent from this install, with their live status. Edit and delete are available while an
 * item is pending. Inherits the app's `MaterialTheme`.
 *
 * @param onBackClick called when the user leaves the list
 * @param onEditClick called with the remote id of a pending item, to open
 * [studio.lunabee.feedback.ui.form.LBFeedbackFormScreen] in edit mode
 */
@Composable
fun LBFeedbackListScreen(
    onBackClick: () -> Unit,
    onEditClick: (remoteId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val graph = LBFeedback.graph()
    val viewModel: LBFeedbackListViewModel = viewModel {
        LBFeedbackListViewModel(client = graph.client)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LBFeedbackListContent(
        state = state,
        onBackClick = onBackClick,
        onEditClick = onEditClick,
        onDeleteClick = viewModel::delete,
        onDismissError = viewModel::dismissError,
        modifier = modifier,
    )
}

@Composable
internal fun LBFeedbackListContent(
    state: LBFeedbackListUiState,
    onBackClick: () -> Unit,
    onEditClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { LBFeedbackTopBar(title = stringResource(Res.string.feedback_list_title), onBackClick = onBackClick) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            state.error?.let {
                TextButton(onClick = onDismissError) {
                    Text(text = feedbackErrorMessage(it), color = MaterialTheme.colorScheme.error)
                }
            }
            if (state.rows.isEmpty()) {
                Text(
                    text = stringResource(Res.string.feedback_list_empty),
                    modifier = Modifier.padding(16.dp).testTag(LBFeedbackTestTags.ListEmpty),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.rows, key = { it.remoteId }) { row ->
                        FeedbackRow(row = row, onEditClick = onEditClick, onDeleteClick = onDeleteClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackRow(
    row: LBFeedbackListRow,
    onEditClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().testTag(LBFeedbackTestTags.listRow(row.remoteId))) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = row.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${stringResource(typeLabel(row.type))} · ${stringResource(statusLabel(row.status))} · ${row.date}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onEditClick(row.remoteId) },
                    enabled = row.isEditable,
                    modifier = Modifier.testTag(LBFeedbackTestTags.editButton(row.remoteId)),
                ) {
                    Text(stringResource(Res.string.feedback_list_edit))
                }
                TextButton(
                    onClick = { onDeleteClick(row.remoteId) },
                    enabled = row.isEditable,
                    modifier = Modifier.testTag(LBFeedbackTestTags.deleteButton(row.remoteId)),
                ) {
                    Text(stringResource(Res.string.feedback_list_delete))
                }
            }
        }
    }
}

private fun typeLabel(type: LBFeedbackType): StringResource = when (type) {
    LBFeedbackType.Bug -> Res.string.feedback_type_bug
    LBFeedbackType.Idea -> Res.string.feedback_type_idea
    LBFeedbackType.Other -> Res.string.feedback_type_other
}

private fun statusLabel(status: LBFeedbackStatus): StringResource = when (status) {
    LBFeedbackStatus.Pending -> Res.string.feedback_status_pending
    LBFeedbackStatus.Declined -> Res.string.feedback_status_declined
    LBFeedbackStatus.Snoozed -> Res.string.feedback_status_snoozed
    LBFeedbackStatus.Accepted -> Res.string.feedback_status_accepted
    LBFeedbackStatus.Duplicate -> Res.string.feedback_status_duplicate
}

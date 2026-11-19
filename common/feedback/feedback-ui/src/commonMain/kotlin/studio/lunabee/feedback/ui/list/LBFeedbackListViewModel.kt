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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import studio.lunabee.feedback.core.client.LBFeedbackClient
import studio.lunabee.feedback.core.model.LBFeedbackItem
import studio.lunabee.feedback.core.transport.LBFeedbackError

internal class LBFeedbackListViewModel(
    private val client: LBFeedbackClient,
) : ViewModel() {
    private val error = MutableStateFlow<LBFeedbackError?>(null)

    private val rows: Flow<List<LBFeedbackListRow>> = client.items
        .map { items -> items.map(::rowOf) }
        .distinctUntilChanged()

    val uiState: StateFlow<LBFeedbackListUiState> = combine(rows, error) { rows, currentError ->
        LBFeedbackListUiState(rows = rows, error = currentError)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LBFeedbackListUiState(),
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { client.refreshStatuses() }
    }

    fun delete(remoteId: String) {
        val row = uiState.value.rows.firstOrNull { it.remoteId == remoteId } ?: return
        if (!row.isEditable) return
        viewModelScope.launch {
            error.value = try {
                client.delete(remoteId)
                null
            } catch (e: LBFeedbackError) {
                e
            }
        }
    }

    fun dismissError() {
        error.value = null
    }

    private fun rowOf(item: LBFeedbackItem): LBFeedbackListRow {
        val dateTime = item.createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
        val time = "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
        return LBFeedbackListRow(
            remoteId = item.remoteId,
            title = item.title,
            type = item.type,
            status = item.status,
            date = "${dateTime.date} $time",
            isEditable = client.canEdit(item),
        )
    }
}

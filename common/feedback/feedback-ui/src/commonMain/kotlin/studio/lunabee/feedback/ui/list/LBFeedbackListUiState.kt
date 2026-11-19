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

import studio.lunabee.feedback.core.model.LBFeedbackStatus
import studio.lunabee.feedback.core.model.LBFeedbackType
import studio.lunabee.feedback.core.transport.LBFeedbackError

internal data class LBFeedbackListUiState(
    val rows: List<LBFeedbackListRow> = emptyList(),
    val error: LBFeedbackError? = null,
)

internal data class LBFeedbackListRow(
    val remoteId: String,
    val title: String,
    val type: LBFeedbackType,
    val status: LBFeedbackStatus,
    val date: String,
    val isEditable: Boolean,
)

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

package studio.lunabee.feedback.ui.form

import studio.lunabee.feedback.core.model.LBFeedbackAttachment
import studio.lunabee.feedback.core.transport.LBFeedbackError

internal sealed interface LBFeedbackFormPhase {
    data object Editing : LBFeedbackFormPhase

    data object Sending : LBFeedbackFormPhase

    data class Failed(val error: LBFeedbackError) : LBFeedbackFormPhase

    data class AttachmentsFailed(
        val remoteId: String,
        val failed: List<LBFeedbackAttachment>,
        val error: LBFeedbackError,
    ) : LBFeedbackFormPhase

    data object Done : LBFeedbackFormPhase
}

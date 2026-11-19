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

package studio.lunabee.feedback.core.client

import studio.lunabee.feedback.core.model.LBFeedbackAttachment
import studio.lunabee.feedback.core.model.LBFeedbackItem
import studio.lunabee.feedback.core.transport.LBFeedbackError

/**
 * Outcome of a send or of a retry of attachments, once the item exists remotely.
 */
sealed interface LBFeedbackSendResult {
    /** The item as stored locally. */
    val item: LBFeedbackItem

    /** The item exists and every attachment was uploaded. */
    data class Sent(override val item: LBFeedbackItem) : LBFeedbackSendResult

    /**
     * The item exists but some attachments are not uploaded.
     *
     * @property failedAttachments the files to give to [LBFeedbackClient.retryAttachments]
     * @property error why the first upload failed
     */
    data class PartiallySent(
        override val item: LBFeedbackItem,
        val failedAttachments: List<LBFeedbackAttachment>,
        val error: LBFeedbackError,
    ) : LBFeedbackSendResult
}

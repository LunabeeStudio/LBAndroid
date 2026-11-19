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

package studio.lunabee.feedback.core.transport

import studio.lunabee.feedback.core.model.LBFeedbackAttachment
import studio.lunabee.feedback.core.model.LBFeedbackDraft
import studio.lunabee.feedback.core.model.LBFeedbackMetadata
import studio.lunabee.feedback.core.model.LBFeedbackRemoteState

/**
 * Link between the library and the remote feedback queue.
 *
 * Every function throws an [LBFeedbackError] on failure.
 */
interface LBFeedbackTransport {
    /**
     * Creates a feedback item remotely. The attachments of [draft] are not uploaded, call [attach] for each.
     *
     * @return remote id of the created item
     */
    suspend fun send(draft: LBFeedbackDraft, metadata: LBFeedbackMetadata): String

    /**
     * Returns the current state of the item [remoteId].
     */
    suspend fun get(remoteId: String): LBFeedbackRemoteState

    /**
     * Replaces the content of the item [remoteId] with [draft], keeping the [metadata] it was sent with.
     */
    suspend fun update(remoteId: String, draft: LBFeedbackDraft, metadata: LBFeedbackMetadata)

    /**
     * Deletes the item [remoteId].
     */
    suspend fun delete(remoteId: String)

    /**
     * Uploads [attachment] to the item [remoteId], at most [LBFeedbackAttachment.MaxBytes]. Separate from [send] so it can
     * be retried alone.
     */
    suspend fun attach(remoteId: String, attachment: LBFeedbackAttachment)
}

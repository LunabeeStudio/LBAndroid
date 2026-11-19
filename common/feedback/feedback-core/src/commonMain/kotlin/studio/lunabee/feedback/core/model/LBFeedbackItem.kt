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

package studio.lunabee.feedback.core.model

import kotlin.time.Instant

/**
 * Feedback already sent, as remembered on this install.
 *
 * Keeps everything needed to rebuild the remote description after an edit.
 *
 * @property remoteId identifier of the item on the remote intake queue
 * @property type kind of feedback
 * @property title short summary
 * @property body detailed text, may be empty
 * @property reporter name of the person who sent it, `null` when not given
 * @property metadata context collected when it was sent
 * @property createdAt time the item was created
 * @property status last known triage state
 */
data class LBFeedbackItem(
    val remoteId: String,
    val type: LBFeedbackType,
    val title: String,
    val body: String,
    val reporter: String?,
    val metadata: LBFeedbackMetadata,
    val createdAt: Instant,
    val status: LBFeedbackStatus,
)

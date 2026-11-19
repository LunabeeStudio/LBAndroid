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

/**
 * Feedback written by the user, before or while it is sent.
 *
 * @property type kind of feedback
 * @property title short summary
 * @property body detailed text, may be empty
 * @property reporter name of the person sending the feedback, `null` when not given
 * @property attachments files to upload, one by one, in separate steps after the feedback is created. Each one is at
 * most [LBFeedbackAttachment.MaxBytes].
 */
class LBFeedbackDraft(
    val type: LBFeedbackType,
    val title: String,
    val body: String,
    val reporter: String? = null,
    val attachments: List<LBFeedbackAttachment> = emptyList(),
)

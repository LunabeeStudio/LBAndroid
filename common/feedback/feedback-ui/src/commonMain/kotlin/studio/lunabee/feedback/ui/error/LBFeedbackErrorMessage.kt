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

package studio.lunabee.feedback.ui.error

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import studio.lunabee.feedback.core.transport.LBFeedbackError
import studio.lunabee.feedback.ui.res.Res
import studio.lunabee.feedback.ui.res.feedback_error_attachment_rejected
import studio.lunabee.feedback.ui.res.feedback_error_network
import studio.lunabee.feedback.ui.res.feedback_error_not_editable
import studio.lunabee.feedback.ui.res.feedback_error_not_found
import studio.lunabee.feedback.ui.res.feedback_error_rate_limited
import studio.lunabee.feedback.ui.res.feedback_error_server
import studio.lunabee.feedback.ui.res.feedback_error_too_large
import studio.lunabee.feedback.ui.res.feedback_error_unauthorized

@Composable
internal fun feedbackErrorMessage(error: LBFeedbackError): String = stringResource(
    when (error) {
        is LBFeedbackError.Unauthorized -> Res.string.feedback_error_unauthorized
        is LBFeedbackError.RateLimited -> Res.string.feedback_error_rate_limited
        is LBFeedbackError.Network -> Res.string.feedback_error_network
        is LBFeedbackError.TooLarge -> Res.string.feedback_error_too_large
        is LBFeedbackError.Server -> Res.string.feedback_error_server
        is LBFeedbackError.AttachmentRejected -> Res.string.feedback_error_attachment_rejected
        is LBFeedbackError.NotEditable -> Res.string.feedback_error_not_editable
        is LBFeedbackError.NotFound -> Res.string.feedback_error_not_found
    },
)

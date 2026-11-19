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

package studio.lunabee.feedback.core.capture

import studio.lunabee.feedback.core.model.LBFeedbackAttachment

/**
 * Captures what the app shows as a JPEG [LBFeedbackAttachment], under [LBFeedbackAttachment.MaxBytes]. On Android, call
 * `install` from `Application.onCreate` first.
 */
expect object LBFeedbackScreenCapture {
    /**
     * Captures the screen on top and returns it, or `null` when there is nothing to capture or the capture fails. Call it
     * from the main thread: only the drawing happens there, the encoding does not.
     */
    suspend fun capture(): LBFeedbackAttachment?
}

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

package studio.lunabee.feedback.ui

import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
import platform.UIKit.modalInPresentation
import studio.lunabee.feedback.core.capture.LBFeedbackScreenCapture

/**
 * iOS entry points of the feedback screens. Call [captureScreenAsPending] before presenting the controller returned by [get]
 * to attach a screenshot of the current screen.
 */
object LBFeedbackController {
    /**
     * Returns the view controller hosting the feedback screens, to present from Swift.
     *
     * @param startOnList `true` to open on "My feedback" instead of the form
     * @param onClose called when the user leaves the flow, dismiss the controller there. The controller is modal in
     * presentation: a swipe down does not dismiss it, so a feedback being sent is never cut off.
     */
    fun get(
        startOnList: Boolean,
        onClose: () -> Unit,
    ): UIViewController = ComposeUIViewController(
        configure = {
            onFocusBehavior = OnFocusBehavior.DoNothing
        },
    ) {
        LBFeedback.Themed {
            LBFeedbackHost(startOnList = startOnList, onClose = onClose)
        }
    }.also { it.modalInPresentation = true }

    /**
     * Captures the key window and keeps it in memory as the screenshot of the next feedback. To get the capture as an
     * attachment instead, use `LBFeedbackScreenCapture.capture()` of `feedback-core`. Does nothing when there is no key
     * window.
     */
    suspend fun captureScreenAsPending() {
        LBFeedbackScreenCapture.capture()?.let { LBFeedback.graph().pending.screenshot.value = it }
    }
}

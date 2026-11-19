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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import studio.lunabee.feedback.core.capture.LBFeedbackCaptureExcluded

/**
 * Activity owned by the library that hosts the feedback screens. Its window is secure: it is hidden from recent apps and
 * cannot be captured, because it shows a capture of the app.
 *
 * Start it with [open] or [LBFeedbackNotification].
 */
class LBFeedbackActivity : ComponentActivity(), LBFeedbackCaptureExcluded {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (savedInstanceState == null && intent.getBooleanExtra(ExtraCapture, false)) {
            captureResumedActivity()
        }
        val startOnList = intent.getBooleanExtra(ExtraStartOnList, false)
        setContent {
            LBFeedback.Themed {
                LBFeedbackHost(startOnList = startOnList, onClose = ::finish)
            }
        }
    }

    internal companion object {
        private const val ExtraStartOnList: String = "studio.lunabee.feedback.ui.START_ON_LIST"
        private const val ExtraCapture: String = "studio.lunabee.feedback.ui.CAPTURE"

        fun intent(context: Context, startOnList: Boolean, capture: Boolean = false): Intent =
            Intent(context, LBFeedbackActivity::class.java)
                .putExtra(ExtraStartOnList, startOnList)
                .putExtra(ExtraCapture, capture)
    }
}

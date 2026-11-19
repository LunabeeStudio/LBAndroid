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

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import studio.lunabee.feedback.core.capture.LBFeedbackScreenCapture
import studio.lunabee.feedback.core.config.LBFeedbackConfig
import studio.lunabee.feedback.core.store.LBFeedbackStore
import studio.lunabee.feedback.core.transport.LBFeedbackTransport
import studio.lunabee.feedback.ui.theme.LBFeedbackTheme

/**
 * Same as [LBFeedback.install], and also tracks the resumed activity so [open] can capture it. Call it once, from
 * `Application.onCreate`.
 */
fun LBFeedback.install(
    application: Application,
    config: LBFeedbackConfig,
    transport: LBFeedbackTransport,
    store: LBFeedbackStore,
    themeWrapper: @Composable (content: @Composable () -> Unit) -> Unit = { LBFeedbackTheme(it) },
) {
    install(config = config, transport = transport, store = store, themeWrapper = themeWrapper)
    LBFeedbackScreenCapture.install(application)
}

/**
 * Captures the activity currently on screen, then opens [LBFeedbackActivity]. Without a tracked activity, or when the
 * capture fails, it opens without a screenshot. The capture is encoded off the main thread and shows up in the form once
 * ready.
 *
 * @param context any context
 * @param startOnList `true` to open on "My feedback" instead of the form
 */
fun LBFeedback.open(context: Context, startOnList: Boolean = false) {
    captureResumedActivity()
    context.startActivity(LBFeedbackActivity.intent(context, startOnList).withNewTaskIfNeeded(context))
}

internal fun Intent.withNewTaskIfNeeded(context: Context): Intent =
    if (context is Activity) this else addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

internal fun captureResumedActivity() {
    val graph = LBFeedback.graph()
    graph.pending.screenshot.value = null
    val bitmap = LBFeedbackScreenCapture.snapshot() ?: return
    graph.scope.launch { graph.pending.screenshot.value = LBFeedbackScreenCapture.encode(bitmap) }
}

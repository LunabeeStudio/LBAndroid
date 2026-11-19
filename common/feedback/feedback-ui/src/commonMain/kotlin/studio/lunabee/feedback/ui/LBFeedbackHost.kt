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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import studio.lunabee.feedback.ui.form.LBFeedbackFormScreen
import studio.lunabee.feedback.ui.host.LBFeedbackHostViewModel
import studio.lunabee.feedback.ui.host.LBFeedbackRoute
import studio.lunabee.feedback.ui.list.LBFeedbackListScreen
import studio.lunabee.feedback.ui.redact.LBFeedbackRedactScreen

/**
 * Hosts the form, redact and list screens with a small back stack, for the screens the library presents itself. Each
 * visit of a screen gets its own ViewModel store, dropped when the screen is popped.
 *
 * @param onClose called when the user leaves the first screen of the flow
 */
@Composable
internal fun LBFeedbackHost(
    startOnList: Boolean,
    onClose: () -> Unit,
) {
    val host: LBFeedbackHostViewModel = viewModel {
        LBFeedbackHostViewModel(if (startOnList) LBFeedbackRoute.List else LBFeedbackRoute.Form)
    }
    val stack by host.stack.collectAsStateWithLifecycle()
    val pop = { if (!host.pop()) onClose() }

    LBFeedbackBackHandler(enabled = stack.size > 1, onBack = pop)

    val entry = stack.last()
    key(entry) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides entry) {
            when (val route = entry.route) {
                LBFeedbackRoute.Form -> LBFeedbackFormScreen(
                    onBackClick = pop,
                    onRedactClick = { host.push(LBFeedbackRoute.Redact) },
                    onFinished = onClose,
                    onListClick = { host.push(LBFeedbackRoute.List) },
                )

                LBFeedbackRoute.Redact -> LBFeedbackRedactScreen(onBackClick = pop, onDone = pop)

                LBFeedbackRoute.List -> LBFeedbackListScreen(
                    onBackClick = pop,
                    onEditClick = { host.push(LBFeedbackRoute.Edit(it)) },
                )

                is LBFeedbackRoute.Edit -> LBFeedbackFormScreen(
                    onBackClick = pop,
                    onRedactClick = { host.push(LBFeedbackRoute.Redact) },
                    onFinished = pop,
                    editRemoteId = route.remoteId,
                )
            }
        }
    }
}

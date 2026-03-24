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

package studio.lunabee.compose.demo.presenter.simple

import androidx.compose.runtime.Composable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import studio.lunabee.compose.presenter.LBSinglePresenter
import javax.inject.Inject

@HiltViewModel
class SimpleExamplePresenter @Inject constructor(
    reducerFactory: SimpleExampleReducerFactory,
) : LBSinglePresenter<SimpleExampleUiState, SimpleExampleNavScope, SimpleExampleAction>(
    reducerFactory = reducerFactory,
    verbose = true,
) {
    override val flows: List<Flow<SimpleExampleAction>> = listOf()

    /**
     * Returns the initial state displayed before any action is reduced.
     */
    override fun getInitialState(): SimpleExampleUiState = SimpleExampleUiState(
        onToggleClick = { emitUserAction(SimpleExampleAction.NewCheckValue(it)) },
        onNewValue = { emitUserAction(SimpleExampleAction.NewValue) },
        onShowToastClick = { emitUserAction(SimpleExampleAction.ShowToast) },
        onShowCascadeToastClick = { emitUserAction(SimpleExampleAction.ShowCascadeToast1) },
        isChecked = false,
        text = "Init",
    )

    override val content: @Composable (SimpleExampleUiState) -> Unit = { SimpleExampleScreen(it) }
}

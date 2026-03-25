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

package studio.lunabee.compose.demo.presenter.pullToRefresh

import androidx.compose.runtime.Composable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import studio.lunabee.compose.presenter.LBFactorySinglePresenter
import javax.inject.Inject

@HiltViewModel
class PullToRefreshPresenter @Inject constructor(
    reducerFactory: PullToRefreshReducerFactory,
) : LBFactorySinglePresenter<PullToRefreshUiState, PullToRefreshNavScope, PullToRefreshAction>(
    reducerFactory = reducerFactory,
    verbose = true,
) {

    override val flows: List<Flow<PullToRefreshAction>> = listOf()

    /**
     * Returns the initial state displayed before any action is reduced.
     */
    override fun getInitialState(): PullToRefreshUiState = PullToRefreshUiState(
        isRefreshing = false,
        refresh = { emitUserAction(PullToRefreshAction.Refresh) },
    )

    override val content: @Composable (PullToRefreshUiState) -> Unit = { PullToRefreshScreen(it) }
}

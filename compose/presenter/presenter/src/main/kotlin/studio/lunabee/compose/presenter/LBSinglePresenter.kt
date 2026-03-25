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

package studio.lunabee.compose.presenter

import androidx.lifecycle.viewModelScope

/**
 * Subclass of [LBPresenter] to implement a single state presenter
 *
 * Override [createReducer] to define how the presenter builds its reducer.
 *
 * @param verbose enable verbose logs using kermit logger
 * @see LBPresenter
 */
abstract class LBSinglePresenter<UiState : PresenterUiState, NavScope : Any, Action> protected constructor(
    verbose: Boolean = false,
) : LBPresenter<UiState, NavScope, Action>(verbose = verbose) {
    /**
     * Creates the single reducer used by this presenter.
     *
     * Override this hook when the reducer depends on values computed from presenter constructor parameters or
     * presenter-owned runtime state.
     */
    protected abstract fun createReducer(runtime: LBReducerRuntime<Action>): LBSingleReducer<UiState, NavScope, Action>

    private val reducer by lazy {
        createReducer(
            LBReducerRuntime(
                coroutineScope = viewModelScope,
                emitUserAction = ::emitUserAction,
            ),
        )
    }

    final override fun getReducerByState(
        actualState: UiState,
    ): LBSingleReducer<UiState, NavScope, Action> = reducer
}

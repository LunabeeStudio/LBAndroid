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
 * Override [createReducer] when reducer creation requires presenter-owned runtime values in addition to
 * dependency-injected factory parameters.
 *
 * @param verbose enable verbose logs using kermit logger
 * @see LBPresenter
 */
abstract class LBSinglePresenter<UiState : PresenterUiState, NavScope : Any, Action> protected constructor(
    private val reducerBuilder: ((LBReducerRuntime<Action>) -> LBSingleReducer<UiState, NavScope, Action>)? = null,
    verbose: Boolean = false,
) : LBPresenter<UiState, NavScope, Action>(verbose = verbose) {
    protected constructor(
        reducerFactory: LBSingleReducerFactory<UiState, NavScope, Action>,
        verbose: Boolean = false,
    ) : this(
        reducerBuilder = { runtime -> reducerFactory.create(runtime) },
        verbose = verbose,
    )

    /**
     * Creates the single reducer used by this presenter.
     *
     * Override this hook when the reducer depends on values computed from presenter constructor parameters or
     * presenter-owned runtime state.
     */
    protected open fun createReducer(runtime: LBReducerRuntime<Action>): LBSingleReducer<UiState, NavScope, Action> =
        checkNotNull(reducerBuilder) {
            "Provide a reducerFactory in the constructor or override createReducer(runtime)"
        }(runtime)

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

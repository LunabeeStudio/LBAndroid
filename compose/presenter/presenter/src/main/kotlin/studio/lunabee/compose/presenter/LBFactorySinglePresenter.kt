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

/**
 * Factory-backed [LBSinglePresenter] that keeps reducer wiring enforced at compile time.
 *
 * Use this base class when the reducer only depends on [LBPresenterContext] values supplied by the presenter and on
 * dependencies already captured by the injected [LBSingleReducerFactory]. Use [LBSinglePresenter] directly when
 * reducer creation also needs presenter-owned runtime values computed in the presenter itself.
 *
 * @param reducerFactory factory used to build the reducer from presenter runtime values
 * @param verbose enable verbose logs using kermit logger
 */
abstract class LBFactorySinglePresenter<UiState : PresenterUiState, NavScope : Any, Action> protected constructor(
    private val reducerFactory: LBSingleReducerFactory<UiState, NavScope, Action>,
    verbose: Boolean = false,
) : LBSinglePresenter<UiState, NavScope, Action>(verbose = verbose) {
    final override fun createReducer(context: LBPresenterContext<Action>): LBSingleReducer<UiState, NavScope, Action> =
        reducerFactory.create(context)
}

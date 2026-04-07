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

package studio.lunabee.compose.demo.navigation.presenter

import android.app.Activity
import kotlinx.coroutines.CoroutineScope
import studio.lunabee.compose.presenter.GenerateReducerFactory
import studio.lunabee.compose.presenter.LBSingleReducer
import studio.lunabee.compose.presenter.ReduceResult
import studio.lunabee.compose.presenter.asResult
import studio.lunabee.compose.presenter.withSideEffect

@GenerateReducerFactory
class TestPresenterReducer(
    override val coroutineScope: CoroutineScope,
    override val emitUserAction: (TestPresenterAction) -> Unit,
) : LBSingleReducer<TestPresenterUiState, TestPresenterNavScope, TestPresenterAction>() {
    override suspend fun reduce(
        actualState: TestPresenterUiState,
        action: TestPresenterAction,
        performNavigation: (TestPresenterNavScope.() -> Unit) -> Unit,
        useActivity: (suspend (Activity) -> Unit) -> Unit,
    ): ReduceResult<TestPresenterUiState> {
        return when (action) {
            TestPresenterAction.Navigate -> actualState withSideEffect {
                performNavigation { navigate() }
            }
            TestPresenterAction.PopModal -> actualState withSideEffect {
                performNavigation { popAllModal() }
            }

            is TestPresenterAction.NewTimer -> actualState.copy(
                timer = action.timer,
            ).asResult()
        }
    }
}

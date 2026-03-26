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

package studio.lunabee.compose.demo.presenter.timer

import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import studio.lunabee.compose.presenter.LBPresenterContext
import studio.lunabee.compose.presenter.LBSingleReducer
import studio.lunabee.compose.presenter.LBSinglePresenter
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class TimerKoinPresenter(
    private val reducerFactory: TimerReducerFactory,
) : LBSinglePresenter<TimerUiState, TimerNavScope, TimerAction>(verbose = true) {
    private val currentTime = Clock.System.now()

    override fun createReducer(context: LBPresenterContext<TimerAction>): LBSingleReducer<TimerUiState, TimerNavScope, TimerAction> =
        reducerFactory.create(
            context = context,
            currentTime = currentTime,
        )

    private val timerFlow: Flow<TimerAction.NewTimerValue> = flow {
        var value = 0
        while (true) {
            emit(value++)
            delay(1.seconds)
        }
    }.map { TimerAction.NewTimerValue(it) }

    override val flows: List<Flow<TimerAction>> = listOf(timerFlow)

    override fun getInitialState(): TimerUiState = TimerUiState(
        timer = "Initial time = $currentTime",
    )

    override val content: @Composable (TimerUiState) -> Unit = { TimerScreen(it) }
    override val topBar: @Composable ((TimerUiState) -> Unit) = {}
}

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

import android.app.Activity
import kotlinx.coroutines.CoroutineScope
import studio.lunabee.compose.presenter.GenerateReducerFactory
import studio.lunabee.compose.presenter.LBSingleReducer
import studio.lunabee.compose.presenter.ReduceResult
import studio.lunabee.compose.presenter.FactoryArg
import studio.lunabee.compose.presenter.asResult
import kotlin.time.Instant

data class TimerInjectedParam(
    val prefix: String,
)

data class TimerRuntimeParam(
    val currentTime: Instant,
)

@GenerateReducerFactory
class TimerReducer(
    override val coroutineScope: CoroutineScope,
    override val emitUserAction: (TimerAction) -> Unit,
    private val injectedParam: TimerInjectedParam,
    @FactoryArg private val runtimeParam: TimerRuntimeParam,
) : LBSingleReducer<TimerUiState, TimerNavScope, TimerAction>(verbose = true) {
    override suspend fun reduce(
        actualState: TimerUiState,
        action: TimerAction,
        performNavigation: (TimerNavScope.() -> Unit) -> Unit,
        useActivity: (suspend (Activity) -> Unit) -> Unit,
    ): ReduceResult<TimerUiState> = when (action) {
        is TimerAction.NewTimerValue -> actualState.copy(
            timer = injectedParam.prefix + Instant.fromEpochSeconds(action.timerValue + runtimeParam.currentTime.epochSeconds),
        ).asResult()
    }
}

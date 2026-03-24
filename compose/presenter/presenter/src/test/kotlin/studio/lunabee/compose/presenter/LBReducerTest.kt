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

import android.app.Activity
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.random.Random

class LBReducerTest {

    @Test
    fun collectReducer_concurrent_test(): TestResult = runTest {
        val coroutineScope = CoroutineScope(Dispatchers.IO) // make sure we have multiple threads

        val flows: List<Flow<TestAction>> = listOf(
            flow {
                repeat(100) {
                    delay(Random.nextLong(0, 1))
                    emit(TestAction.Increment0)
                }
            },
            flow {
                repeat(100) {
                    delay(Random.nextLong(0, 1))
                    emit(TestAction.Increment1)
                }
            },
            flow {
                repeat(100) {
                    delay(Random.nextLong(0, 1))
                    emit(TestAction.Increment2)
                }
            },
        )

        val reducer = TestReducer(
            coroutineScope = coroutineScope,
            emitUserAction = {},
        )

        var actualStateSaved = TestUiState(count = 0)
        val uiStateFlow = reducer
            .collectReducer(
                flows = flows,
                actualState = { actualStateSaved },
                performNavigation = {},
                useActivity = {},
            ).onEach { actualStateSaved = it }
            .stateIn(coroutineScope, started = SharingStarted.WhileSubscribed(5_000), actualStateSaved)

        uiStateFlow.first { it.count == 300 }

        assertEquals(TestUiState(count = 300), actualStateSaved)
    }

    private class TestReducer(
        override val coroutineScope: CoroutineScope,
        override val emitUserAction: (TestAction) -> Unit,
    ) : LBSingleReducer<TestUiState, TestNavScope, TestAction>(verbose = true) {

        override suspend fun reduce(
            actualState: TestUiState,
            action: TestAction,
            performNavigation: (TestNavScope.() -> Unit) -> Unit,
            useActivity: (suspend (Activity) -> Unit) -> Unit,
        ): ReduceResult<TestUiState> =
            when (action) {
                TestAction.Increment0 -> actualState.copy(count = actualState.count + 1).asResult()
                TestAction.Increment1 -> actualState.copy(count = actualState.count + 1).asResult()
                TestAction.Increment2 -> actualState.copy(count = actualState.count + 1).asResult()
            }
    }

    private data class TestUiState(
        val count: Int,
    ) : PresenterUiState

    private interface TestNavScope

    private sealed interface TestAction {
        data object Increment0 : TestAction

        data object Increment1 : TestAction

        data object Increment2 : TestAction
    }
}

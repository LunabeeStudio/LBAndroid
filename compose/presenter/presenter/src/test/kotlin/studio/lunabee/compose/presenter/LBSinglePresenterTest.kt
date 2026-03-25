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
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import studio.lunabee.compose.robolectrictest.LbcInjectComponentActivityRule
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LBSinglePresenterTest {

    @get:Rule(order = Rule.DEFAULT_ORDER - 1)
    val addActivityToRobolectricRule: TestWatcher = LbcInjectComponentActivityRule()

    @get:Rule
    val rule: ComposeContentTestRule = createComposeRule()

    @Before
    fun setup() {
        Logger.addLogWriter(CommonWriter())
    }

    @Test
    fun multi_useActivity_call_test(): TestResult = runTest {
        val reducerFactory = ActivityTestReducerFactory()
        val presenter = ActivityTestPresenter(reducerFactory)

        assertEquals(0, reducerFactory.createCount, "Reducer factory should stay lazy until the reducer is requested")

        val reducer = presenter.getReducerByState(TestUiState) as ActivityTestReducer

        assertEquals(1, reducerFactory.createCount, "Reducer factory should be invoked once")
        assertSame(reducer, presenter.getReducerByState(TestUiState), "Reducer should be cached by the presenter")

        rule.setContent {
            presenter.invoke(Unit)
        }

        presenter.emitUserAction(TestAction.TestAction0)

        rule.waitForIdle()
        advanceUntilIdle()

        assertTrue(reducer.action1, "action1 should be true")
        assertTrue(reducer.action2, "action2 should be true")
    }

    @Test
    fun reducer_runtime_callback_test(): TestResult = runTest {
        val reducerFactory = ActivityTestReducerFactory()
        val presenter = ActivityTestPresenter(reducerFactory)
        val reducer = presenter.getReducerByState(TestUiState) as ActivityTestReducer

        assertSame(presenter.viewModelScope, reducerFactory.context.coroutineScope, "Factory should receive the presenter scope")

        rule.setContent {
            presenter.invoke(Unit)
        }

        reducerFactory.context.emitUserAction(TestAction.TestAction0)

        rule.waitForIdle()
        advanceUntilIdle()

        assertSame(reducer, presenter.getReducerByState(TestUiState), "Reducer should still be reused")
        assertTrue(reducer.action1, "action1 should be true when the runtime callback emits an action")
        assertTrue(reducer.action2, "action2 should be true when the runtime callback emits an action")
    }

    @Test
    fun reducer_runtime_args_factory_test(): TestResult = runTest {
        val reducerFactory = ActivityRuntimeArgsReducerFactory()
        val presenter = ActivityRuntimeArgsPresenter(reducerFactory)
        val reducer = presenter.getReducerByState(TestUiState) as ActivityRuntimeArgsReducer

        rule.setContent {
            presenter.invoke(Unit)
        }

        presenter.emitUserAction(TestAction.TestAction0)

        rule.waitForIdle()
        advanceUntilIdle()

        assertSame(presenter.viewModelScope, reducerFactory.context.coroutineScope, "Factory should receive the presenter scope")
        assertEquals("runtime-value", reducerFactory.factoryArg, "Factory should receive factory args from the presenter")
        assertTrue(reducer.usedFactoryArg, "Reducer should receive the presenter factory args")
    }

    @Test
    fun reducer_can_use_presenter_computed_runtime_value_test(): TestResult = runTest {
        val reducerFactory = ActivityRuntimeArgsReducerFactory()
        val presenter = ActivitySharedRuntimePresenter(
            reducerFactory = reducerFactory,
            prefix = "shared",
        )

        presenter.getReducerByState(TestUiState)

        assertEquals(
            presenter.presenterRuntimeValue,
            reducerFactory.factoryArg,
            "Reducer should receive the runtime value computed and reused by the presenter",
        )
    }

    private class ActivityTestPresenter(
        reducerFactory: ActivityTestReducerFactory,
    ) : LBFactorySinglePresenter<TestUiState, Unit, TestAction>(
        reducerFactory = reducerFactory,
        verbose = true,
    ) {
        override val flows: List<Flow<TestAction>> = emptyList()

        override fun getInitialState(): TestUiState = TestUiState

        override val content: @Composable ((TestUiState) -> Unit) = {}
    }

    private class ActivityTestReducerFactory : LBSingleReducerFactory<TestUiState, Unit, TestAction> {
        var createCount: Int = 0
        lateinit var context: LBPresenterContext<TestAction>

        override fun create(context: LBPresenterContext<TestAction>): ActivityTestReducer {
            createCount++
            this.context = context
            return ActivityTestReducer(
                coroutineScope = context.coroutineScope,
                emitUserAction = context.emitUserAction,
            )
        }
    }

    private class ActivityRuntimeArgsPresenter(
        private val reducerFactory: ActivityRuntimeArgsReducerFactory,
    ) : LBSinglePresenter<TestUiState, Unit, TestAction>(verbose = true) {
        override fun createReducer(context: LBPresenterContext<TestAction>): ActivityRuntimeArgsReducer =
            reducerFactory.create(
                context = context,
                factoryArg = "runtime-value",
            )

        override val flows: List<Flow<TestAction>> = emptyList()

        override fun getInitialState(): TestUiState = TestUiState

        override val content: @Composable ((TestUiState) -> Unit) = {}
    }

    private class ActivitySharedRuntimePresenter(
        private val reducerFactory: ActivityRuntimeArgsReducerFactory,
        prefix: String,
    ) : LBSinglePresenter<TestUiState, Unit, TestAction>(verbose = true) {
        val presenterRuntimeValue: String = "$prefix-runtime"

        override fun createReducer(context: LBPresenterContext<TestAction>): ActivityRuntimeArgsReducer =
            reducerFactory.create(
                context = context,
                factoryArg = presenterRuntimeValue,
            )

        override val flows: List<Flow<TestAction>> = emptyList()

        override fun getInitialState(): TestUiState = TestUiState

        override val content: @Composable ((TestUiState) -> Unit) = {}
    }

    private class ActivityRuntimeArgsReducerFactory {
        lateinit var context: LBPresenterContext<TestAction>
        var factoryArg: String = ""

        fun create(
            context: LBPresenterContext<TestAction>,
            factoryArg: String,
        ): ActivityRuntimeArgsReducer {
            this.context = context
            this.factoryArg = factoryArg
            return ActivityRuntimeArgsReducer(
                coroutineScope = context.coroutineScope,
                emitUserAction = context.emitUserAction,
                runtimeValue = factoryArg,
            )
        }
    }

    private class ActivityTestReducer(
        override val coroutineScope: CoroutineScope,
        override val emitUserAction: (TestAction) -> Unit,
    ) : LBSingleReducer<TestUiState, Unit, TestAction>(verbose = true) {

        var action1 = false
        var action2 = false

        override suspend fun reduce(
            actualState: TestUiState,
            action: TestAction,
            performNavigation: (Unit.() -> Unit) -> Unit,
            useActivity: (suspend (Activity) -> Unit) -> Unit,
        ): ReduceResult<TestUiState> =
            when (action) {
                TestAction.TestAction0 -> actualState.withSideEffect {
                    useActivity {
                        action1 = true
                    }
                    emitUserAction(TestAction.TestAction1)
                }

                TestAction.TestAction1 -> actualState.withSideEffect {
                    useActivity {
                        action2 = true
                    }
                }
            }
    }

    private class ActivityRuntimeArgsReducer(
        override val coroutineScope: CoroutineScope,
        override val emitUserAction: (TestAction) -> Unit,
        private val runtimeValue: String,
    ) : LBSingleReducer<TestUiState, Unit, TestAction>(verbose = true) {
        var usedFactoryArg: Boolean = false

        override suspend fun reduce(
            actualState: TestUiState,
            action: TestAction,
            performNavigation: (Unit.() -> Unit) -> Unit,
            useActivity: (suspend (Activity) -> Unit) -> Unit,
        ): ReduceResult<TestUiState> =
            when (action) {
                TestAction.TestAction0 -> actualState.withSideEffect {
                    usedFactoryArg = runtimeValue == "runtime-value"
                }

                TestAction.TestAction1 -> actualState.asResult()
            }
    }

    private object TestUiState : PresenterUiState {
        override fun toString(): String = "TestUiState"
    }

    private sealed interface TestAction {
        data object TestAction0 : TestAction

        data object TestAction1 : TestAction
    }
}

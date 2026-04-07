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

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import studio.lunabee.compose.core.LbcTextSpec
import studio.lunabee.compose.demo.core.topbar.CoreTopBar
import studio.lunabee.compose.demo.core.topbar.action.CoreTopBarAction
import studio.lunabee.compose.navigation.LbcNavigationScreen
import studio.lunabee.compose.presenter.LBPresenterContext
import studio.lunabee.compose.presenter.LBSinglePresenter

class TestPresenterPresenter(
    private val title: String,
    private val reducerFactory: TestPresenterReducerFactory,
) : LbcNavigationScreen<TestPresenterNavScope>,
    LBSinglePresenter<TestPresenterUiState, TestPresenterNavScope, TestPresenterAction>() {

    val timer: Flow<Long> = flow {
        var timer = 0L
        while (true) {
            delay(1000)
            emit(timer++)
        }
    }

    override val flows: List<Flow<TestPresenterAction>> = listOf(
        timer.map { TestPresenterAction.NewTimer(it) },
    )

    override fun createReducer(context: LBPresenterContext<TestPresenterAction>): TestPresenterReducer =
        reducerFactory.create(
            context = context,
        )

    override fun getInitialState(): TestPresenterUiState = TestPresenterUiState(
        onNavigate = { emitUserAction(TestPresenterAction.Navigate) },
        timer = 0L,
        onPop = { emitUserAction(TestPresenterAction.PopModal) },
    )

    override val content: @Composable (TestPresenterUiState) -> Unit = {
        TestPresenterScreen(it)
    }

    @Composable
    override fun Screen(navScope: TestPresenterNavScope) {
        invoke(navScope)
    }

    @Composable
    override fun TopBar() {
        val uiState by uiStateFlow.collectAsStateWithLifecycle()
        topBar(uiState)
    }

    val topBar: @Composable ((TestPresenterUiState) -> Unit) = {
        if (title != "Bottom Sheet B") {
            CoreTopBar(
                title = LbcTextSpec.Raw(title),
                leadingAction = CoreTopBarAction.backAction(
                    onClick = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher::onBackPressed,
                ),
                trailingAction = CoreTopBarAction.Custom {
                    Text(text = it.timer.toString())
                },
                modifier = Modifier
                    .background(Color.Gray)
                    .padding(bottom = it.timer.toInt().dp),
            )
        }
    }
}

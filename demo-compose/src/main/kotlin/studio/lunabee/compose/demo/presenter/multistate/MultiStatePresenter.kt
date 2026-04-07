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

package studio.lunabee.compose.demo.presenter.multistate

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import studio.lunabee.compose.presenter.LBPresenter
import studio.lunabee.compose.presenter.LBSimpleReducer
import javax.inject.Inject

@HiltViewModel
class MultiStatePresenter @Inject constructor() : LBPresenter<MultiStateUiState, MultiStateNavScope, MultiStateAction>() {

    private val flow: Flow<MultiStateAction> = flow {
        while (true) {
            delay(2000)
            val actions = listOf(
                MultiStateAction.ExampleAction,
                MultiStateAction.ExampleAllAction,
                MultiStateAction.ExampleErrorAction,
            )
            emit(actions.random())
        }
    }

    override val flows: List<Flow<MultiStateAction>> = listOf(flow)

    override fun getInitialState(): MultiStateUiState = MultiStateUiState.Data("unknown")

    override fun getReducerByState(actualState: MultiStateUiState):
        LBSimpleReducer<MultiStateUiState, MultiStateNavScope, MultiStateAction> = when (actualState) {
        is MultiStateUiState.Data -> MultiStateDataReducer(
            coroutineScope = viewModelScope,
            emitUserAction = ::emitUserAction,
        )

        is MultiStateUiState.Error -> MultiStateErrorReducer(
            coroutineScope = viewModelScope,
            emitUserAction = ::emitUserAction,
        )
    }

    override val content: @Composable (MultiStateUiState) -> Unit = {
        Column {
            Text("reduced by ${it.reducer}")
            when (it) {
                is MultiStateUiState.Data -> Text("Data State")
                is MultiStateUiState.Error -> Text("Error State")
            }
        }
    }
}

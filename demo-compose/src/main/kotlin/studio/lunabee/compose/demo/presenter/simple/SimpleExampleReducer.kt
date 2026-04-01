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

package studio.lunabee.compose.demo.presenter.simple

import android.app.Activity
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import studio.lunabee.compose.presenter.GenerateReducerFactory
import studio.lunabee.compose.presenter.LBSingleReducer
import studio.lunabee.compose.presenter.ReduceResult
import studio.lunabee.compose.presenter.asResult
import studio.lunabee.compose.presenter.withSideEffect
import kotlin.random.Random

@GenerateReducerFactory
class SimpleExampleReducer(
    override val coroutineScope: CoroutineScope,
    override val emitUserAction: (SimpleExampleAction) -> Unit,
) : LBSingleReducer<SimpleExampleUiState, SimpleExampleNavScope, SimpleExampleAction>(verbose = true) {
    override suspend fun reduce(
        actualState: SimpleExampleUiState,
        action: SimpleExampleAction,
        performNavigation: (SimpleExampleNavScope.() -> Unit) -> Unit,
        useActivity: (suspend (Activity) -> Unit) -> Unit,
    ): ReduceResult<SimpleExampleUiState> = when (action) {
        is SimpleExampleAction.NewCheckValue -> actualState.copy(isChecked = action.value).asResult()

        SimpleExampleAction.NewValue -> actualState.copy(text = Random.nextInt().toString()).asResult()

        SimpleExampleAction.ShowToast -> actualState withSideEffect {
            useActivity { context ->
                Toast.makeText(context, "Toast from reducer with ui context", Toast.LENGTH_SHORT).show()
            }
        }

        SimpleExampleAction.ShowCascadeToast1 -> actualState withSideEffect {
            useActivity { context ->
                Log.d("multi_use_activity", "1st use activity call")
                Toast.makeText(context, "1st toast", Toast.LENGTH_SHORT).show()
            }
            emitUserAction(SimpleExampleAction.ShowCascadeToast2)
        }

        SimpleExampleAction.ShowCascadeToast2 -> actualState withSideEffect {
            useActivity { context ->
                Log.d("multi_use_activity", "2nd use activity call")
                Toast.makeText(context, "2nd toast", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

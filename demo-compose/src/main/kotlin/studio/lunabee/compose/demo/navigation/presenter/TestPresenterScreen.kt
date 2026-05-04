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
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import studio.lunabee.compose.core.LbcTextSpec
import studio.lunabee.compose.demo.core.topbar.CoreTopBar
import studio.lunabee.compose.demo.core.topbar.action.CoreTopBarAction
import studio.lunabee.compose.navigation.utils.animatedTopBarModifier

@Composable
fun TestPresenterScreen(
    uiState: TestPresenterUiState,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        TopBar(title = uiState.title, timer = uiState.timer)
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = uiState.onNavigate,
            ) {
                Text(text = "Navigate")
            }

            Button(
                onClick = uiState.onPop,
            ) {
                Text(text = "Pop")
            }
            val value = remember { mutableStateOf("") }
            OutlinedTextField(
                value = value.value,
                onValueChange = {
                    value.value = it
                },
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun TopBar(title: String, timer: Long) {
    CoreTopBar(
        title = LbcTextSpec.Raw(title),
        leadingAction = CoreTopBarAction.backAction(
            onClick = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher::onBackPressed,
        ),
        trailingAction = CoreTopBarAction.Custom {
            Text(text = timer.toString())
        },
        modifier = Modifier
            .animatedTopBarModifier(),
    )
}

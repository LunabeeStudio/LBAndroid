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

package studio.lunabee.compose.navigation

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import studio.lunabee.compose.navigation.utils.normalPopTransition
import studio.lunabee.compose.navigation.utils.normalPredictivePopTransition
import studio.lunabee.compose.navigation.utils.normalPushTransition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LbcNavHost(
    backStack: NavBackStack<LbcNavigationKey>,
    onBack: () -> Unit = { backStack.removeLastOrNull() },
) {
    val bottomSheetStrategy = remember { ModalSceneStrategy<LbcNavigationKey>() }
    val navigationHelper = remember { NavigationHelper(backStack) }
    val presenterRegistry = rememberScreenRegistry()

    Box {
        val localDensity = LocalDensity.current
        val localTopBarSize = remember { mutableStateOf(0.dp) }
        Box(
            modifier = Modifier
                .animateContentSize()
                .onSizeChanged {
                    localTopBarSize.value = with(localDensity) { it.height.toDp() }
                },
        ) {
            backStack.lastOrNull { !it.isModal }?.let { entry ->
                presenterRegistry.get(entry.id)?.TopBar()
            }
        }
        CompositionLocalProvider(
            LocalBackStack provides backStack,
            LocalScreenRegistry provides presenterRegistry,
            LocalTopBarPadding provides localTopBarSize.value,
        ) {
            NavDisplay(
                backStack = backStack,
                sceneStrategies = listOf(bottomSheetStrategy),
                onBack = onBack,
                transitionSpec = { normalPushTransition() },
                popTransitionSpec = { normalPopTransition() },
                predictivePopTransitionSpec = { _: @NavigationEvent.SwipeEdge Int -> normalPredictivePopTransition() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = { route ->
                    if (route.isModal) {
                        NavEntry(
                            key = route,
                            contentKey = route.id,
                            metadata = ModalSceneStrategy.modal(
                                groupId = route.resolvedModalGroupId(),
                            ),
                        ) {
                            Box(modifier = Modifier.fillMaxHeight(route.modalHeightFraction)) {
                                val presenter = route.destination.present(navigationHelper)
                                RegisterNavigationScreen(route.id, presenter)
                            }
                        }
                    } else {
                        NavEntry(
                            key = route,
                            contentKey = route.id,
                        ) {
                            val presenter = route.destination.present(navigationHelper)
                            RegisterNavigationScreen(route.id, presenter)
                        }
                    }
                },
            )
        }
    }
}

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

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import studio.lunabee.compose.navigation.utils.LocalModalBackgroundColor
import studio.lunabee.compose.navigation.utils.LocalNavHostAnimatedVisibilityScope
import studio.lunabee.compose.navigation.utils.LocalNavHostSharedTransitionScope
import studio.lunabee.compose.navigation.utils.normalPopTransition
import studio.lunabee.compose.navigation.utils.normalPredictivePopTransition
import studio.lunabee.compose.navigation.utils.normalPushTransition

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun LbcNavHost(
    backStack: NavBackStack<LbcNavigationKey>,
    navigationHelper: NavigationHelper,
    modalBackgroundColor: Color,
    onBack: () -> Unit = { backStack.removeLastOrNull() },
) {
    val bottomSheetStrategy = remember { ModalSceneStrategy<LbcNavigationKey>() }

    Box {
        CompositionLocalProvider(
            LocalBackStack provides backStack,
            LocalModalBackgroundColor provides modalBackgroundColor,
        ) {
            SharedTransitionLayout {
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
                                val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                                val padding = screenHeight * (1f - route.modalHeightFraction)
                                val density = LocalDensity.current
                                val imeHeight = with(density) { WindowInsets.ime.getBottom(density).toDp() }
                                Box(
                                    modifier = Modifier
                                        .height(screenHeight - padding - imeHeight),
                                ) {
                                    route.destination.Present(navigationHelper)
                                }
                            }
                        } else {
                            NavEntry(
                                key = route,
                                contentKey = route.id,
                            ) {
                                CompositionLocalProvider(
                                    LocalNavHostSharedTransitionScope provides this@SharedTransitionLayout,
                                    LocalNavHostAnimatedVisibilityScope provides LocalNavAnimatedContentScope.current,
                                ) {
                                    route.destination.Present(navigationHelper)
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

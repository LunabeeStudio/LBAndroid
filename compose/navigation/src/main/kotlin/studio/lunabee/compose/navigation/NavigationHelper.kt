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

import androidx.navigation3.runtime.NavBackStack
import kotlin.reflect.KClass
import kotlin.uuid.Uuid

class NavigationHelper internal constructor(
    private val backStack: NavBackStack<CoreNavigationKey>,
) {

    fun popBackStack(popupTo: KClass<out CoreDestination<*>>? = null, inclusive: Boolean = false) {
        popupTo?.let {
            if (backStack.any { it.screen::class == popupTo }) {
                while (backStack.lastOrNull()?.let { it.screen::class != popupTo } == true) {
                    backStack.removeLastOrNull()
                }
                if (inclusive) backStack.removeLastOrNull()
            }
        } ?: run {
            backStack.removeLastOrNull()
        }
    }

    fun navigate(coreDestination: CoreDestination<*>, popupTo: KClass<out CoreDestination<*>>? = null, inclusive: Boolean = false) {
        popupTo?.let { popBackStack(popupTo, inclusive) }
        val actualScreen = backStack.lastOrNull()
        if (actualScreen?.isBottomSheet == true) {
            backStack.add(
                CoreNavigationKey(
                    isBottomSheet = true,
                    screen = coreDestination,
                    bottomSheetGroupId = actualScreen.resolvedBottomSheetGroupId(),
                    bottomSheetHeightFraction = actualScreen.bottomSheetHeightFraction,
                ),
            )
        } else {
            backStack.add(CoreNavigationKey(isBottomSheet = false, screen = coreDestination))
        }
    }

    fun modal(coreDestination: CoreDestination<*>, popupTo: KClass<out CoreDestination<*>>? = null, inclusive: Boolean = false) {
        popupTo?.let { popBackStack(popupTo, inclusive) }
        val actualScreen = backStack.lastOrNull()
        backStack.add(
            CoreNavigationKey(
                isBottomSheet = true,
                screen = coreDestination,
                bottomSheetGroupId = Uuid.random(),
                bottomSheetHeightFraction = if (actualScreen?.isBottomSheet == true) {
                    actualScreen.bottomSheetHeightFraction - 0.03f
                } else {
                    CoreNavigationKey.DefaultBottomSheetHeightFraction
                },
            ),
        )
    }
}

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

import android.content.Context
import androidx.navigation3.runtime.NavBackStack
import kotlin.reflect.KClass
import kotlin.uuid.Uuid

class NavigationHelper(
    private val parentContext: Context,
    private val backStack: NavBackStack<LbcNavigationKey>,
) {

    val context
        get() = parentContext

    fun popBackStack(popupTo: PopUpTo? = null) {
        when (popupTo) {
            is PopUpTo.Class -> {
                if (backStack.any { it.destination::class == popupTo.clazz }) {
                    while (backStack.lastOrNull()?.let { it.destination::class != popupTo.clazz } == true) {
                        backStack.removeLastOrNull()
                    }
                    if (popupTo.inclusive) backStack.removeLastOrNull()
                }
            }

            is PopUpTo.Instance -> {
                if (backStack.any { it.destination != popupTo.destination }) {
                    while (backStack.lastOrNull()?.let { it.destination == popupTo } == true) {
                        backStack.removeLastOrNull()
                    }
                    if (popupTo.inclusive) backStack.removeLastOrNull()
                }
            }

            null -> backStack.removeLastOrNull()
        }
    }

    /**
     * Pop every modal entry on top of the stack, leaving the topmost non-modal entry in place.
     * Useful for flows whose terminal screens need to close the whole modal group without
     * hard-coding a specific host destination class.
     */
    fun popAllModals() {
        while (backStack.lastOrNull()?.isModal == true) {
            backStack.removeLastOrNull()
        }
    }

    fun navigate(lbcDestination: LbcDestination<*>, popupTo: PopUpTo? = null) {
        popupTo?.let { popBackStack(popupTo) }
        val actualScreen = backStack.lastOrNull()
        if (actualScreen?.isModal == true) {
            backStack.add(
                LbcNavigationKey(
                    isModal = true,
                    destination = lbcDestination,
                    modalGroupId = actualScreen.resolvedModalGroupId(),
                    modalHeightFraction = actualScreen.modalHeightFraction,
                ),
            )
        } else {
            backStack.add(LbcNavigationKey(isModal = false, destination = lbcDestination))
        }
    }

    fun modal(lbcDestination: LbcDestination<*>, popupTo: PopUpTo? = null) {
        popupTo?.let { popBackStack(popupTo) }
        val actualScreen = backStack.lastOrNull()
        backStack.add(
            LbcNavigationKey(
                isModal = true,
                destination = lbcDestination,
                modalGroupId = Uuid.random(),
                modalHeightFraction = if (actualScreen?.isModal == true) {
                    actualScreen.modalHeightFraction - 0.03f
                } else {
                    LbcNavigationKey.DefaultModalHeightFraction
                },
            ),
        )
    }
}

sealed interface PopUpTo {
    val inclusive: Boolean

    data class Class(val clazz: KClass<out LbcDestination<*>>, override val inclusive: Boolean = false) : PopUpTo
    data class Instance(val destination: LbcDestination<*>, override val inclusive: Boolean = false) : PopUpTo
}

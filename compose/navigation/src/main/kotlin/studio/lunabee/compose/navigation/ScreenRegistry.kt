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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.uuid.Uuid

val LocalScreenRegistry: ProvidableCompositionLocal<ScreenRegistry?> = staticCompositionLocalOf { null }

data class PresenterEntry(
    val id: Uuid,
    val presenter: LbcNavigationScreen<*>,
)

class ScreenRegistry {
    private val presenters = mutableStateListOf<PresenterEntry>()

    fun get(key: Uuid): LbcNavigationScreen<*>? = presenters.lastOrNull { it.id == key }?.presenter ?: presenters.lastOrNull()?.presenter

    fun put(key: Uuid, screen: LbcNavigationScreen<*>) {
        presenters.add(PresenterEntry(key, screen))
    }

    fun remove(key: Uuid) {
        presenters.removeAll { it.id == key }
    }
}

@Composable
fun rememberScreenRegistry(): ScreenRegistry = remember { ScreenRegistry() }

@Composable
fun RegisterNavigationScreen(
    key: Uuid,
    screen: LbcNavigationScreen<*>,
) {
    val registry = LocalScreenRegistry.current ?: return
    DisposableEffect(key, screen, registry) {
        registry.put(key, screen)
        onDispose { registry.remove(key) }
    }
}

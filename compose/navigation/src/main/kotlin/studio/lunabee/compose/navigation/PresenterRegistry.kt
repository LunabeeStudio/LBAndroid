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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import studio.lunabee.compose.presenter.LBPresenter
import kotlin.uuid.Uuid

val LocalPresenterRegistry: ProvidableCompositionLocal<PresenterRegistry?> = staticCompositionLocalOf { null }

class PresenterRegistry {
    private val presenters = mutableStateMapOf<Uuid, LBPresenter<*, *, *>>()

    fun get(key: Uuid): LBPresenter<*, *, *>? = presenters[key] ?: presenters.entries.lastOrNull()?.value

    fun put(key: Uuid, presenter: LBPresenter<*, *, *>) {
        presenters[key] = presenter
    }

    fun remove(key: Uuid, presenter: LBPresenter<*, *, *>) {
        if (presenters[key] === presenter) {
            presenters.remove(key)
        }
    }
}

@Composable
fun rememberPresenterRegistry(): PresenterRegistry = remember { PresenterRegistry() }

@Composable
fun RegisterPresenterEffect(
    key: Uuid,
    presenter: LBPresenter<*, *, *>,
) {
    val registry = LocalPresenterRegistry.current ?: return
    DisposableEffect(key, presenter, registry) {
        registry.put(key, presenter)
        onDispose {
            registry.remove(key, presenter)
        }
    }
}

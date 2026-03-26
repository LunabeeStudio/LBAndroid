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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

@Composable
fun rememberLbcNavBackStack(
    serializersModule: SerializersModule = EmptySerializersModule(),
    vararg startDestinations: CoreNavigationKey,
): NavBackStack<CoreNavigationKey> {
    val configuration = remember(serializersModule) {
        SavedStateConfiguration {
            this.serializersModule = serializersModule
        }
    }

    return rememberSerializable(
        serializer = NavBackStackSerializer(elementSerializer = NavKeySerializer()),
        configuration = configuration,
    ) {
        NavBackStack(*startDestinations)
    }
}

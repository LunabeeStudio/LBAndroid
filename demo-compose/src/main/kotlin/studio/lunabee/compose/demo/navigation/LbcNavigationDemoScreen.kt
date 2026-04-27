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

package studio.lunabee.compose.demo.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import studio.lunabee.compose.navigation.LbcDestination
import studio.lunabee.compose.navigation.LbcNavHost
import studio.lunabee.compose.navigation.LbcNavigationKey
import studio.lunabee.compose.navigation.NavigationHelper
import studio.lunabee.compose.navigation.rememberLbcNavBackStack

@Composable
fun LbcNavigationDemoScreen() {
    val destinationSerializersModule = remember {
        SerializersModule {
            polymorphic(LbcDestination::class) {
                subclass(ScreenA::class)
                subclass(ScreenB::class)
                subclass(BottomSheet::class)
                subclass(BottomSheet2::class)
            }
        }
    }
    val backStack = rememberLbcNavBackStack(
        serializersModule = destinationSerializersModule,
        LbcNavigationKey(false, ScreenA()),
    )
    val context = LocalContext.current
    val navigationHelper = remember(backStack) { NavigationHelper(context, backStack) }
    LbcNavHost(
        backStack,
        navigationHelper,
        MaterialTheme.colorScheme.background,
    )

    LaunchedEffect(backStack.size) {
        println("backstack -> ${backStack.joinToString { "%s %s".format(it.destination.toString(), it.id.toString()) }}")
    }
}

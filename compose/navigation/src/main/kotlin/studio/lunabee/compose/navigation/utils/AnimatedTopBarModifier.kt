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

package studio.lunabee.compose.navigation.utils

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import studio.lunabee.compose.navigation.LocalAnimatedVisibilityScope
import studio.lunabee.compose.navigation.LocalSharedTransitionScope

fun Modifier.animatedTopBarModifier(): Modifier = composed {
    @OptIn(ExperimentalSharedTransitionApi::class)
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    return@composed if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            this@composed.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "293b1bdb-5af4-4add-9ed5-0b1a331cdcfc"),
                enter = fadeIn(),
                exit = fadeOut(),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else {
        this@composed
    }
}

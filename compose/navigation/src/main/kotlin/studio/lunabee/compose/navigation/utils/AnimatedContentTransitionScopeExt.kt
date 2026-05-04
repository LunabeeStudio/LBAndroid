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

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith

fun AnimatedContentTransitionScope<*>.normalPushTransition(): ContentTransform =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(durationMillis = 300),
        initialOffset = { fullWidth -> fullWidth },
    ) togetherWith slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(durationMillis = 300),
        targetOffset = { fullWidth -> fullWidth },
    )

fun AnimatedContentTransitionScope<*>.normalPopTransition(): ContentTransform =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = tween(durationMillis = 300),
        initialOffset = { fullWidth -> fullWidth },
    ) togetherWith slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = tween(durationMillis = 300),
        targetOffset = { fullWidth -> fullWidth },
    )

fun AnimatedContentTransitionScope<*>.normalPredictivePopTransition(): ContentTransform =
    normalPopTransition()

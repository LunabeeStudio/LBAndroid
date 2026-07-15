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

package studio.lunabee.loading

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Add an empty [BackHandler] to block back action according to [LoadingManager] state
 */
@Composable
fun LoadingBlockBackView(
    loadingManager: LoadingManager,
) {
    val loadingState by loadingManager.loadingState.collectAsStateWithLifecycle()
    BackHandler(
        enabled = loadingState.isBlocking,
    ) {}
}

object BlockingViewScope

interface LoadingViewScope : AnimatedVisibilityScope {
    val loadingContentDescription: String?
}

internal class LoadingViewScopeImpl(
    private val animatedVisibilityScope: AnimatedVisibilityScope,
    override val loadingContentDescription: String?,
) : LoadingViewScope,
    AnimatedVisibilityScope by animatedVisibilityScope

/**
 * Show a loading spinner according to [LoadingManager] state
 *
 * @param blockBackPress true block the back press, false to only show the loading
 * @param loadingContent the actual loading composable to show
 */
@Composable
fun LoadingView(
    contentDescription: String?,
    loadingManager: LoadingManager,
    modifier: Modifier = Modifier,
    blockBackPress: Boolean = true,
    blockingContent: @Composable (BlockingViewScope.() -> Unit) = { DefaultBlockingContent() },
    loadingContent: @Composable (LoadingViewScope.() -> Unit) = { DefaultLoadingContent() },
) {
    val loadingState by loadingManager.loadingState.collectAsStateWithLifecycle()
    if (blockBackPress) {
        LoadingBlockBackView(loadingManager)
    }
    if (loadingState.isBlocking) {
        BlockingViewScope.blockingContent()
    }
    AnimatedLoadingContent(modifier, loadingState.isLoading, contentDescription, loadingContent)
}

@Composable
private fun AnimatedLoadingContent(
    modifier: Modifier,
    showLoading: Boolean,
    contentDescription: String?,
    loadingContent: @Composable (LoadingViewScope.() -> Unit),
) {
    AnimatedVisibility(
        modifier = modifier,
        visible = showLoading,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        LoadingViewScopeImpl(this, contentDescription).loadingContent()
    }
}

/**
 * A default loading composable to be used in LoadingView
 */
@Composable
context(loadingViewScope: LoadingViewScope)
fun DefaultLoadingContent(
    modifier: Modifier = Modifier,
    background: Brush = SolidColor(MaterialTheme.colorScheme.scrim.copy(ScrimOpacity)),
    progressIndicator: @Composable BoxScope.() -> Unit = { CircularProgressIndicator(Modifier.align(Alignment.Center)) },
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clearAndSetSemantics {
                loadingViewScope.loadingContentDescription?.let { loadingContentDescription ->
                    liveRegion = LiveRegionMode.Polite
                    this.contentDescription = loadingContentDescription
                }
            }
            .then(modifier)
            .drawBehind { drawRect(background) },
    ) {
        progressIndicator()
    }
}

/**
 * A default blocking composable to be used in LoadingView
 */
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("UnusedReceiverParameter")
@Composable
fun BlockingViewScope.DefaultBlockingContent(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = {}, enabled = false)
            .semantics {
                hideFromAccessibility()
            },
    )
}

private const val ScrimOpacity = 0.32f // use the same value as bottom sheet m3

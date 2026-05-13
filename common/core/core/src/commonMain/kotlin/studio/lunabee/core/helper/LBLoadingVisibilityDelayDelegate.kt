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

package studio.lunabee.core.helper

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import studio.lunabee.logger.LBLogger
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Helper class to show/hide loading state or view with a delay to avoid blink on tiny loading time
 *
 * @property minLoadingShowDuration Minimum duration that the loading will be displayed
 * @property delayBeforeShow Delay before showing the loading
 */
class LBLoadingVisibilityDelayDelegate(
    var minLoadingShowDuration: Duration = DefaultMinLoadingShowDurationMs.milliseconds,
    var delayBeforeShow: Duration = DefaultDelayBeforeShowMs.milliseconds,
) {

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var pendingShow: AtomicBoolean = AtomicBoolean(false)

    private var showJob: Job? = null
    private var hideJob: Job? = null

    /**
     * Request the loading to be hide. Cancels the loading show request if [delayBeforeShow] has
     * not been reached and immediately call [hideLoading] or wait until [showJob] is done to call
     * the [hideLoading] block.
     *
     * @param hideLoading Block called to hide the loading
     */
    fun delayHideLoading(hideLoading: suspend () -> Unit) {
        if (hideJob?.isActive == true) {
            logger.v("Ignoring delayHideLoading call because hideJob is already active")
            return // ignore multiple call
        }

        if (pendingShow.exchange(false)) {
            showJob?.cancel("State hide called")
            coroutineScope.launch {
                hideLoading()
            }
        } else {
            hideJob = coroutineScope.launch {
                showJob?.join()
                hideLoading()
            }
        }
    }

    /**
     * Request the loading to be show. Cancels the pending hide request if exist and call
     * [showLoading] block after [delayBeforeShow] has been reached.
     *
     * @param updateLoading Block called even if the loading is already show. Will also be call on first show just after [showLoading]
     * @param showLoading Block called to display the loading
     */
    fun delayShowLoading(
        updateLoading: suspend () -> Unit = {},
        showLoading: suspend () -> Unit,
    ) {
        hideJob?.cancel("State show called")

        if (showJob?.isActive == true) {
            coroutineScope.launch {
                updateLoading()
            }
            return // ignore multiple call
        }

        if (pendingShow.compareAndSet(expectedValue = false, newValue = true)) {
            showJob = coroutineScope.launch {
                delay(delayBeforeShow)
                showLoading()
                updateLoading()
                pendingShow.exchange(false)
                delay(minLoadingShowDuration)
            }
        }
    }

    /**
     * @return True if a hide or show job is active
     */
    fun isActive(): Boolean = showJob?.isActive == true || hideJob?.isActive == true

    companion object {
        const val DefaultMinLoadingShowDurationMs: Long = 500L
        const val DefaultDelayBeforeShowMs: Long = 500L
    }
}

private val logger: Logger = LBLogger.get<LBLoadingVisibilityDelayDelegate>()

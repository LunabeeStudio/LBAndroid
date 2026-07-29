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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ShowDelayedLoadingTest {

    @get:Rule
    val composeTestRule: ComposeContentTestRule = createComposeRule()

    /**
     * [LBLoadingVisibilityDelayDelegate] schedules on [kotlinx.coroutines.Dispatchers.Main] in real
     * time, not on the Compose test clock, so every delay asserted here must dwarf composition and
     * frame work — a short one races with a loaded CI agent. Exact timing is covered deterministically
     * by `LBLoadingVisibilityDelayDelegateTest` with a virtual-time dispatcher.
     */
    private val testDelay: Duration = 2.seconds

    @Test
    fun rememberShowDelayedLoading_no_loading_test() {
        var actualShowLoading: Boolean? = null

        composeTestRule.setContent {
            val showLoading: Boolean by rememberShowDelayedLoading(
                shouldShowLoading = false,
                minLoadingShowDuration = Duration.ZERO,
                delayBeforeShow = Duration.ZERO,
            )

            actualShowLoading = showLoading
        }

        assertFalse(actualShowLoading!!)
    }

    @Test
    fun rememberShowDelayedLoading_show_no_delay_test() {
        var actualShowLoading: Boolean? = null

        composeTestRule.setContent {
            val showLoading: Boolean by rememberShowDelayedLoading(
                shouldShowLoading = true,
                minLoadingShowDuration = Duration.ZERO,
                delayBeforeShow = Duration.ZERO,
            )

            actualShowLoading = showLoading
        }

        assertTrue(actualShowLoading!!)
    }

    @Test
    fun rememberShowDelayedLoading_show_after_delay_test() {
        var actualShowLoading: Boolean? = null
        val delayBeforeShow = testDelay

        composeTestRule.setContent {
            val showLoading: Boolean by rememberShowDelayedLoading(
                shouldShowLoading = true,
                minLoadingShowDuration = Duration.ZERO,
                delayBeforeShow = delayBeforeShow,
            )

            actualShowLoading = showLoading
        }

        assertFalse(actualShowLoading!!)
        composeTestRule.waitUntil(timeoutMillis = (delayBeforeShow * 3).inWholeMilliseconds) { actualShowLoading == true }
    }

    @Test
    fun rememberShowDelayedLoading_hide_no_delay_test(): TestResult = runTest {
        var actualShowLoading: Boolean? = null
        val shouldShowLoading = mutableStateOf(true)

        composeTestRule.setContent {
            val showLoading: Boolean by rememberShowDelayedLoading(
                shouldShowLoading = shouldShowLoading.value,
                minLoadingShowDuration = Duration.ZERO,
                delayBeforeShow = Duration.ZERO,
            )

            actualShowLoading = showLoading
        }

        assertTrue(actualShowLoading!!)
        shouldShowLoading.value = false
        composeTestRule.waitUntil { !actualShowLoading }
    }

    @Test
    fun rememberShowDelayedLoading_hide_min_loading_test() {
        var actualShowLoading: Boolean? = null
        val shouldShowLoading = mutableStateOf(true)
        val minLoadingShowDuration = testDelay

        composeTestRule.setContent {
            val showLoading: Boolean by rememberShowDelayedLoading(
                shouldShowLoading = shouldShowLoading.value,
                minLoadingShowDuration = minLoadingShowDuration,
                delayBeforeShow = Duration.ZERO,
            )

            actualShowLoading = showLoading
        }

        // Immediately true
        assertTrue(actualShowLoading!!)

        // Request false
        shouldShowLoading.value = false
        composeTestRule.mainClock.advanceTimeByFrame()
        assertTrue(actualShowLoading) // Still true because of minLoadingShowDuration

        composeTestRule.waitUntil(timeoutMillis = (minLoadingShowDuration * 3).inWholeMilliseconds) { actualShowLoading == false }
    }

    @Test
    fun rememberShowDelayedLoading_hide_before_delay_test(): TestResult = runTest {
        val actualShowLoading = mutableListOf<Boolean>()
        val shouldShowLoading = mutableStateOf(true)
        val delayBeforeShow = testDelay
        val minLoadingShowDuration = Duration.INFINITE

        composeTestRule.setContent {
            val showLoading: Boolean by rememberShowDelayedLoading(
                shouldShowLoading = shouldShowLoading.value,
                minLoadingShowDuration = minLoadingShowDuration,
                delayBeforeShow = delayBeforeShow,
            )

            actualShowLoading.add(showLoading)
        }

        // Hidden initially because of the show delay
        assertFalse(actualShowLoading.last())

        // Request hide before the show delay can elapse: this cancels the pending show
        shouldShowLoading.value = false
        composeTestRule.mainClock.advanceTimeByFrame()
        assertFalse(actualShowLoading.last())

        // Wait well beyond the show delay; the cancelled show must never become visible
        composeTestRule.wait(delayBeforeShow * 1.5)
        assertFalse(actualShowLoading.any { it })
    }
}

private suspend fun ComposeTestRule.wait(delay: Duration) {
    withContext(Dispatchers.IO) {
        delay(delay)
    }
    mainClock.advanceTimeByFrame()
}

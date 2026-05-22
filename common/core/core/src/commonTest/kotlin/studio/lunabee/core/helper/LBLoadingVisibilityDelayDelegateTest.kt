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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class LBLoadingVisibilityDelayDelegateTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun isActive_test(): TestResult = runTest {
        val actualShowLoading = mutableListOf<Boolean>()
        val delayMs = 2L

        val delegate = LBLoadingVisibilityDelayDelegate(
            minLoadingShowDuration = delayMs.milliseconds,
            delayBeforeShow = delayMs.milliseconds,
        )

        // Request show
        delegate.delayShowLoading { actualShowLoading += true }
        assertTrue(delegate.isActive())

        testDispatcher.scheduler.advanceTimeBy(delayMs)
        testDispatcher.scheduler.runCurrent()
        assertTrue(delegate.isActive()) // still true to handle minLoadingShowDuration

        testDispatcher.scheduler.advanceTimeBy(delayMs)
        testDispatcher.scheduler.runCurrent()
        assertFalse(delegate.isActive()) // false after minLoadingShowDuration

        // Request hide
        delegate.delayHideLoading { actualShowLoading += false }
        assertTrue(delegate.isActive()) // briefly true has hide job did not run yet

        testDispatcher.scheduler.runCurrent()

        assertFalse(delegate.isActive())
    }

    @Test
    fun delayShowLoading_during_pending_hide_test(): TestResult = runTest {
        val actualShowLoading = mutableListOf<Boolean>()
        val delayMs = 2L

        val delegate = LBLoadingVisibilityDelayDelegate(
            minLoadingShowDuration = delayMs.milliseconds,
            delayBeforeShow = delayMs.milliseconds,
        )

        // Request show
        delegate.delayShowLoading { actualShowLoading += true }
        // Advance delayBeforeShow + run delayShowLoading lambda
        testDispatcher.scheduler.advanceTimeBy(delayMs)
        testDispatcher.scheduler.runCurrent()
        // Request hide
        delegate.delayHideLoading { actualShowLoading += false }
        // Advance partially minLoadingShowDuration
        testDispatcher.scheduler.advanceTimeBy(delayMs / 2)
        // Re-request show
        delegate.delayShowLoading { actualShowLoading += true }
        // Advance to last state
        testDispatcher.scheduler.advanceUntilIdle()

        val expectedShowLoading = listOf(true)
        assertContentEquals(expectedShowLoading, actualShowLoading, "actual = ${actualShowLoading.joinToString()}")
    }

    @Test
    fun delayShowLoading_updateProgress_test(): TestResult = runTest {
        val actualShowLoading = mutableListOf<Boolean>()
        val delayMs = 2L

        val delegate = LBLoadingVisibilityDelayDelegate(
            minLoadingShowDuration = delayMs.milliseconds,
            delayBeforeShow = delayMs.milliseconds,
        )

        var progress = -1

        // Request show
        delegate.delayShowLoading(
            updateLoading = { progress = 0 },
            showLoading = { actualShowLoading += true },
        )
        // Advance delayBeforeShow + run delayShowLoading lambda (show + update)
        testDispatcher.scheduler.advanceTimeBy(delayMs)
        testDispatcher.scheduler.runCurrent()

        assertEquals(0, progress)

        // Call delayShowLoading to update progress
        delegate.delayShowLoading(
            updateLoading = { progress = 1 },
            showLoading = { assertTrue(false, "Should not be called") },
        )

        testDispatcher.scheduler.runCurrent()

        assertEquals(1, progress)
    }
}

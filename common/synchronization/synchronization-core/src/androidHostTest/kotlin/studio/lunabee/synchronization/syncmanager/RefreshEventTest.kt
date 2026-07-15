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

package studio.lunabee.synchronization.syncmanager

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import studio.lunabee.synchronization.LBSyncGroup
import studio.lunabee.synchronization.LBSyncOperator
import studio.lunabee.synchronization.testfixture.FakeSyncManager
import studio.lunabee.synchronization.testfixture.runManagerTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * [LBSyncRefreshEvent.isDelayElapsed] debounce truth table and the operator's receiver-triggered refresh
 * (`triggerRefresh` sets matched groups' managers to [LBSyncProcessStatus.PendingSync] and launches the
 * sync detached).
 *
 * `isDelayElapsed` reads `Clock.System.now()` internally, so deltas are kept far from the boundary
 * (1 ms vs 1 hour against a small debounce) to stay non-flaky.
 */
class RefreshEventTest {

    @AfterTest
    fun clearOperator() {
        LBSyncOperator.groups.clear()
    }

    // region isDelayElapsed truth table

    @Test
    fun app_foreground_delay_not_elapsed_returns_false() {
        val event = LBSyncRefreshEvent.AppForeground(minimumDelay = 30.seconds)
        // last sync only 1 ms ago: well within the 30 s debounce → not elapsed.
        val recent = Clock.System.now() - 1.milliseconds

        assertFalse(event.isDelayElapsed(recent), "a sync 1 ms ago is inside the 30 s debounce")
    }

    @Test
    fun app_foreground_delay_elapsed_returns_true() {
        val event = LBSyncRefreshEvent.AppForeground(minimumDelay = 30.seconds)
        // last sync an hour ago: far past the 30 s debounce → elapsed.
        val old = Clock.System.now() - 1.hours

        assertTrue(event.isDelayElapsed(old), "a sync an hour ago is past the 30 s debounce")
    }

    @Test
    fun app_foreground_zero_delay_is_always_elapsed() {
        val event = LBSyncRefreshEvent.AppForeground(minimumDelay = Duration.ZERO)
        // Even a sync 1 ms ago counts as elapsed with a zero debounce.
        val recent = Clock.System.now() - 1.milliseconds

        assertTrue(event.isDelayElapsed(recent), "a zero debounce is always elapsed for any past instant")
    }

    @Test
    fun internet_is_back_shares_the_same_delay_semantics() {
        val event = LBSyncRefreshEvent.InternetIsBack(minimumDelay = 30.seconds)
        val now = Clock.System.now()

        assertFalse(event.isDelayElapsed(now - 1.milliseconds), "InternetIsBack: a recent sync is inside the debounce")
        assertTrue(event.isDelayElapsed(now - 1.hours), "InternetIsBack: an hour-old sync is past the debounce")
    }

    // endregion

    // region operator receiver-trigger

    @Test
    fun trigger_refresh_marks_matched_group_managers_pending_sync() = runManagerTest { store, scope ->
        val manager = FakeSyncManager(store = store, scope = scope, retryTempo = null)
        val recorded = mutableListOf<LBSyncProcessStatus>()
        // Unconfined collector resumes synchronously on each emission, capturing the PendingSync transition.
        scope.launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.status.collect { recorded += it }
        }

        LBSyncOperator.groups["g"] = LBSyncGroup(
            syncManagers = linkedSetOf(manager),
            refreshEvents = listOf(LBSyncRefreshEvent.AppForeground(minimumDelay = Duration.ZERO)),
        )

        // triggerRefresh sets PendingSync synchronously on every matched manager before launching the sync
        // detached. The actual pipeline only advances when the test scheduler does, so until then PendingSync
        // is the latest recorded transition.
        LBSyncOperator.triggerRefresh(LBSyncRefreshEvent.AppForeground::class)

        assertTrue(
            LBSyncProcessStatus.PendingSync in recorded,
            "the matched group's manager transitions through PendingSync",
        )

        // Tidy up the detached run so it does not leak into the next test.
        manager.cancelAllRequests()
        advanceUntilIdle()
    }

    @Test
    fun trigger_refresh_ignores_groups_carrying_a_different_event_type() = runManagerTest { store, scope ->
        val manager = FakeSyncManager(store = store, scope = scope, retryTempo = null)
        LBSyncOperator.groups["g"] = LBSyncGroup(
            syncManagers = linkedSetOf(manager),
            // Carries InternetIsBack only; we fire AppForeground → no match, no PendingSync.
            refreshEvents = listOf(LBSyncRefreshEvent.InternetIsBack(minimumDelay = Duration.ZERO)),
        )

        LBSyncOperator.triggerRefresh(LBSyncRefreshEvent.AppForeground::class)

        assertEquals(
            expected = LBSyncProcessStatus.NeverSync,
            actual = manager.currentSyncStatus,
            "a group carrying only a different event type is not refreshed (stays NeverSync)",
        )
    }

    // endregion
}

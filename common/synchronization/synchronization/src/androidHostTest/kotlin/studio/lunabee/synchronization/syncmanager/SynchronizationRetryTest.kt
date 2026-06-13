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

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import studio.lunabee.core.model.LBResult
import studio.lunabee.synchronization.testfixture.FakeSyncManager
import studio.lunabee.synchronization.testfixture.LocalObj
import studio.lunabee.synchronization.testfixture.ServerObj
import studio.lunabee.synchronization.testfixture.runManagerTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Manager-level automatic-retry behaviour, driven by [SyncRunner] under virtual time: a failed run is
 * re-run after `retryTempo`, a `null` tempo disables it, a cancel pre-empts a pending retry, and an
 * intermittently-failing push eventually succeeds on the retry. Also covers cursor persistence across
 * two sequential `synchronize()` calls.
 */
class SynchronizationRetryTest {

    // region retry after tempo

    @Test
    fun a_failed_sync_is_re_run_only_after_the_retry_tempo_elapses() = runManagerTest { store, scope ->
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            uploadObjects = listOf(LocalObj("a")),
            pushError = IllegalStateException("boom"),
            retryTempo = 30.seconds,
        )

        // runCurrent (not advanceUntilIdle): the first run settles at the current virtual instant while the
        // scheduled-but-future retry is left parked — advanceUntilIdle would otherwise fire the perpetually
        // failing retry forever.
        val caller: Deferred<LBResult<Unit>> = async { manager.synchronize() }
        runCurrent()

        assertTrue(caller.await() is LBResult.Failure, "the first failed run resolves the caller with Failure")
        val fetchesAfterFirstRun = manager.fetchCalls
        val pushesAfterFirstRun = manager.pushCalls
        assertEquals(expected = 1, actual = fetchesAfterFirstRun, "exactly one download on the first run")
        assertEquals(expected = 1, actual = pushesAfterFirstRun, "exactly one push on the first run")

        // Just shy of the tempo: no re-run yet.
        advanceTimeBy(29.seconds)
        runCurrent()
        assertEquals(expected = fetchesAfterFirstRun, actual = manager.fetchCalls, "no re-run before the tempo elapses")
        assertEquals(expected = pushesAfterFirstRun, actual = manager.pushCalls, "no re-push before the tempo elapses")

        // Crossing the tempo fires the retry exactly once (runCurrent keeps time from cascading onto the
        // next rescheduled retry).
        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(expected = fetchesAfterFirstRun + 1, actual = manager.fetchCalls, "the retry re-runs the pipeline after the tempo")
        assertEquals(expected = pushesAfterFirstRun + 1, actual = manager.pushCalls, "the retry re-attempts the push after the tempo")
    }

    // endregion

    // region retry-then-succeed

    @Test
    fun a_transient_push_failure_succeeds_on_the_retry() = runManagerTest { store, scope ->
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            uploadObjects = listOf(LocalObj("a")),
            pushErrorOnAttempt = 1, // only the first push throws; the retry's push succeeds
            retryTempo = 30.seconds,
        )

        val caller: Deferred<LBResult<Unit>> = async { manager.synchronize() }
        runCurrent()
        assertTrue(caller.await() is LBResult.Failure, "the first attempt fails")
        assertEquals(expected = 1, actual = manager.pushCalls, "only the first (failing) push so far")

        // Fire the retry, which now pushes successfully. It succeeds so no further retry is scheduled and
        // advanceUntilIdle is safe to drain the terminal status write.
        advanceTimeBy(30.seconds)
        advanceUntilIdle()

        assertTrue(
            manager.currentSyncStatus is LBSyncProcessStatus.SyncSuccessfully,
            "the retry succeeds, ending on SyncSuccessfully",
        )
        assertEquals(expected = 2, actual = manager.pushCalls, "the push was attempted twice: failed then succeeded")
    }

    // endregion

    // region retry disabled

    @Test
    fun a_null_retry_tempo_disables_automatic_retry() = runManagerTest { store, scope ->
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            uploadObjects = listOf(LocalObj("a")),
            pushError = IllegalStateException("boom"),
            retryTempo = null,
        )

        val caller: Deferred<LBResult<Unit>> = async { manager.synchronize() }
        advanceUntilIdle()
        assertTrue(caller.await() is LBResult.Failure)
        val pushesAfterFailure = manager.pushCalls

        // No matter how much virtual time passes, a null tempo never re-runs.
        advanceTimeBy(10.seconds)
        advanceUntilIdle()
        assertEquals(expected = pushesAfterFailure, actual = manager.pushCalls, "a null tempo never schedules a retry")
        assertEquals(expected = 1, actual = pushesAfterFailure, "only the single failing push ran")
    }

    // endregion

    // region cancel pre-empts retry

    @Test
    fun cancel_after_a_failure_pre_empts_the_pending_retry() = runManagerTest { store, scope ->
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            uploadObjects = listOf(LocalObj("a")),
            pushError = IllegalStateException("boom"),
            retryTempo = 30.seconds,
        )

        // runCurrent settles the first failed run at the current instant; the +30s retry stays parked.
        val caller: Deferred<LBResult<Unit>> = async { manager.synchronize() }
        runCurrent()
        assertTrue(caller.await() is LBResult.Failure)
        val fetchesBeforeCancel = manager.fetchCalls

        // Cancel BEFORE advancing onto the retry instant: it must pre-empt the pending retry.
        manager.cancelAllRequests()
        runCurrent()

        // Past the original retry instant: the cancelled retry must NOT fire (runCurrent keeps time from
        // cascading; a re-armed retry would only exist if the cancel failed to pre-empt).
        advanceTimeBy(60.seconds)
        runCurrent()
        assertEquals(
            expected = fetchesBeforeCancel,
            actual = manager.fetchCalls,
            "cancelAllRequests pre-empts the pending retry: no re-run after the tempo",
        )
        assertTrue(
            manager.currentSyncStatus is LBSyncProcessStatus.DownloadFinishSuccessfully,
            "cancel surfaces the legacy DownloadFinishSuccessfully terminal status",
        )
    }

    // endregion

    // region cursor persistence across runs

    @Test
    fun the_server_cursor_persists_across_two_sequential_synchronize_calls() = runManagerTest { store, scope ->
        val cursorMillis = 12_345L
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            pages = listOf(
                FetchPage<ServerObj, Int>(
                    objects = listOf(ServerObj(updatedAt = Instant.fromEpochMilliseconds(cursorMillis))),
                ),
            ),
            supportIncremental = true,
            supportChangeNotification = true, // single download per run keeps fetchArgs crisp
        )

        // First run saves the cursor.
        manager.synchronize()
        assertEquals(
            expected = cursorMillis,
            actual = store.lastServerSyncDate(syncKey = manager.syncKey),
            "the first run persists the server cursor",
        )

        // Second run's first fetch is seeded with the persisted cursor.
        manager.synchronize()

        assertEquals(
            expected = Instant.fromEpochMilliseconds(cursorMillis),
            actual = manager.fetchArgs.last().sinceLastDate,
            "the second run's first fetch is seeded with the cursor saved by the first run",
        )
    }

    // endregion
}

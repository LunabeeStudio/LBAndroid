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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import studio.lunabee.core.model.LBResult
import studio.lunabee.synchronization.store.SyncKey
import studio.lunabee.synchronization.testfixture.FakeSyncManager
import studio.lunabee.synchronization.testfixture.LocalObj
import studio.lunabee.synchronization.testfixture.ServerObj
import studio.lunabee.synchronization.testfixture.runManagerTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class LBSyncManagerTest {

    // region pipeline ordering

    @Test
    fun pipeline_runs_download_then_upload_then_re_download_in_order() = runManagerTest { store, scope ->
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            uploadObjects = listOf(LocalObj("a")),
        )

        val result = manager.synchronize()

        assertTrue(result is LBResult.Success, "a clean pipeline returns Success")
        assertEquals(expected = 2, actual = manager.fetchCalls, "download then re-download = two fetches")
        assertEquals(expected = 1, actual = manager.pushCalls, "upload runs once between the two downloads")
        assertEquals(
            expected = listOf("fetch", "update", "push", "fetch", "update"),
            actual = manager.callLog,
            "pipeline order is download → upload → re-download",
        )
    }

    @Test
    fun no_re_download_when_server_notifications_are_supported() = runManagerTest { store, scope ->
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            uploadObjects = listOf(LocalObj("a")),
            supportChangeNotification = true,
        )

        manager.synchronize()

        assertEquals(expected = 1, actual = manager.fetchCalls, "no re-download when the server notifies of changes")
        assertEquals(expected = 1, actual = manager.pushCalls)
        assertEquals(
            expected = listOf("fetch", "update", "push"),
            actual = manager.callLog,
        )
    }

    @Test
    fun no_re_download_when_nothing_to_upload() = runManagerTest { store, scope ->
        val manager = FakeSyncManager(store = store, scope = scope, uploadObjects = emptyList())

        manager.synchronize()

        // Empty upload short-circuits, so even without server notifications there is no second download.
        assertEquals(expected = 1, actual = manager.fetchCalls)
        assertEquals(expected = 0, actual = manager.pushCalls)
        assertEquals(expected = listOf("fetch", "update"), actual = manager.callLog)
    }

    // endregion

    // region incremental cursor persistence

    @Test
    fun incremental_cursor_is_saved_when_incremental_sync_is_supported() = runManagerTest { store, scope ->
        val maxDate = Instant.fromEpochMilliseconds(5_000L)
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            pages = listOf(FetchPage<ServerObj, Int>(objects = listOf(ServerObj(updatedAt = maxDate)))),
            supportIncremental = true,
        )

        manager.synchronize()

        assertEquals(
            expected = maxDate,
            actual = store.lastServerSyncDate(syncKey = manager.syncKey),
            "the max ascending updatedAt is persisted as the server cursor",
        )
    }

    @Test
    fun terminal_success_persists_the_server_cursor_even_without_incremental_sync() = runManagerTest { store, scope ->
        val maxDate = Instant.fromEpochMilliseconds(5_000L)
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            pages = listOf(FetchPage<ServerObj, Int>(objects = listOf(ServerObj(updatedAt = maxDate)))),
            supportIncremental = false,
        )

        manager.synchronize()

        assertEquals(
            expected = maxDate,
            actual = store.lastServerSyncDate(syncKey = manager.syncKey),
            "a successful download persists the server cursor for legacy parity, even without incremental sync",
        )
    }

    @Test
    fun incremental_sync_does_not_checkpoint_an_open_max_when_a_later_page_fails() = runManagerTest { store, scope ->
        // Page 0 is a full page of a single timestamp (so paging continues), then page 1 throws. That
        // timestamp is still open — page 1 could have carried more records sharing it — so under the strict
        // `>` resume filter nothing is checkpointed, and the next run re-fetches from the prior cursor.
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            pages = listOf(FetchPage(objects = listOf(ServerObj(updatedAt = Instant.fromEpochMilliseconds(100L))))),
            pageSize = 1,
            fetchErrorOnPage = 1,
            supportIncremental = true,
        )

        val result = manager.synchronize()

        assertTrue(result is LBResult.Failure, "a mid-paging fetch failure surfaces as Failure")
        assertNull(
            store.lastServerSyncDate(syncKey = manager.syncKey),
            "an unclosed max (only one timestamp seen) is not persisted mid-paging",
        )
    }

    @Test
    fun non_incremental_sync_persists_no_cursor_when_a_later_page_fails() = runManagerTest { store, scope ->
        // Same shape, but non-incremental: nothing is checkpointed per page and the failure aborts before
        // the terminal save, so no cursor is persisted.
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            pages = listOf(FetchPage(objects = listOf(ServerObj(updatedAt = Instant.fromEpochMilliseconds(100L))))),
            pageSize = 1,
            fetchErrorOnPage = 1,
            supportIncremental = false,
        )

        val result = manager.synchronize()

        assertTrue(result is LBResult.Failure, "a mid-paging fetch failure surfaces as Failure")
        assertNull(
            store.lastServerSyncDate(syncKey = manager.syncKey),
            "without per-page checkpointing a failed multi-page download persists no cursor",
        )
    }

    // endregion

    // region paging

    @Test
    fun download_loops_over_every_page_of_a_multi_page_fetch() = runManagerTest { store, scope ->
        val pages = listOf<FetchPage<ServerObj, Int>>(
            FetchPage(objects = List(size = 2) { ServerObj(updatedAt = Instant.fromEpochMilliseconds(it + 1L)) }),
            FetchPage(objects = List(size = 2) { ServerObj(updatedAt = Instant.fromEpochMilliseconds(it + 10L)) }),
            FetchPage(objects = listOf(ServerObj(updatedAt = Instant.fromEpochMilliseconds(100L)))),
        )
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            pages = pages,
            pageSize = 2,
            supportChangeNotification = true, // skip re-download so fetch count reflects only the paging loop
        )

        manager.synchronize()

        // Pages of 2, 2, 1 with pageSize 2: the loop stops once a page is short (1 < 2).
        assertEquals(expected = 3, actual = manager.fetchCalls, "paging loops until a short page")
        assertEquals(
            expected = listOf(2, 2, 1),
            actual = manager.updatedPageSizes,
            "every page is forwarded to updateData",
        )
    }

    // endregion

    // region status sequences

    @Test
    fun success_emits_the_expected_status_sequence() = runManagerTest { store, scope ->
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            uploadObjects = listOf(LocalObj("a")),
        )
        val statuses = manager.recordStatuses(scope, testScheduler)

        manager.synchronize()
        advanceUntilIdle()

        assertEquals(
            expected = listOf(
                "NeverSync",
                "DownloadStarted",
                "DownloadUpdated",
                "DownloadFinishSuccessfully",
                "UploadStarted",
                "UploadFinishSuccessfully",
                "DownloadStarted",
                "DownloadUpdated",
                "DownloadFinishSuccessfully",
                "SyncSuccessfully",
            ),
            actual = statuses(),
        )
    }

    @Test
    fun download_error_emits_download_finish_with_error_and_returns_failure() = runManagerTest { store, scope ->
        val boom = IllegalStateException("download boom")
        val manager = FakeSyncManager(store = store, scope = scope, fetchError = boom, retryTempo = null)
        val statuses = manager.recordStatuses(scope, testScheduler)

        val result = manager.synchronize()
        advanceUntilIdle()

        assertTrue(result is LBResult.Failure, "a download error surfaces as Failure")
        assertEquals(expected = boom, actual = result.throwable)
        assertEquals(
            expected = listOf("NeverSync", "DownloadStarted", "DownloadFinishWithError"),
            actual = statuses(),
        )
    }

    @Test
    fun upload_error_emits_upload_finish_with_error_and_returns_failure() = runManagerTest { store, scope ->
        val boom = IllegalStateException("upload boom")
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            uploadObjects = listOf(LocalObj("a")),
            pushError = boom,
            retryTempo = null,
        )
        val statuses = manager.recordStatuses(scope, testScheduler)

        val result = manager.synchronize()
        advanceUntilIdle()

        assertTrue(result is LBResult.Failure, "an upload error surfaces as Failure")
        assertEquals(expected = boom, actual = result.throwable)
        assertEquals(
            expected = listOf(
                "NeverSync",
                "DownloadStarted",
                "DownloadUpdated",
                "DownloadFinishSuccessfully",
                "UploadStarted",
                "UploadFinishWithError",
            ),
            actual = statuses(),
        )
    }

    @Test
    fun cancel_surfaces_cancelled_terminal_status() = runManagerTest { store, scope ->
        val gate = CompletableDeferred<Unit>()
        val manager = FakeSyncManager(store = store, scope = scope, fetchGate = gate, retryTempo = null)

        val caller: Deferred<LBResult<Unit>> = async { manager.synchronize() }
        advanceUntilIdle() // park inside the first fetch (the gate is never completed)

        // Cancel kills the parked in-flight run; its awaiter resolves as Failure and the terminal status
        // set synchronously by cancelAllRequests() survives (the cancelled pipeline sets nothing further).
        manager.cancelAllRequests()
        advanceUntilIdle()

        assertTrue(
            manager.currentSyncStatus is LBSyncProcessStatus.Cancelled,
            "cancel surfaces the Cancelled terminal status",
        )
        assertTrue(
            !manager.currentSyncStatus.isProcessing(),
            "the Cancelled status is terminal, so isProcessing (and isSyncing) is false after a cancel",
        )
        assertTrue(caller.await() is LBResult.Failure, "a cancelled in-flight synchronize resolves as Failure")
    }

    @Test
    fun external_scope_cancellation_surfaces_cancelled_status() = runManagerTest { store, _ ->
        val runScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        val manager = FakeSyncManager(store = store, scope = runScope, fetchGate = gate, retryTempo = null)

        runScope.launch { manager.synchronize() }
        advanceUntilIdle() // park inside the first fetch (the gate is never completed)

        // Cancel the scope directly — NOT via cancelAllRequests (which sets the status itself) — so only
        // runPipeline's cancellation path can produce the terminal status.
        runScope.cancel()
        advanceUntilIdle()

        assertTrue(
            manager.currentSyncStatus is LBSyncProcessStatus.Cancelled,
            "an external scope cancel still surfaces the Cancelled terminal status via runPipeline",
        )
    }

    // endregion

    // region load() seeding

    @Test
    fun load_seeds_status_from_a_pre_populated_store() = runManagerTest { store, scope ->
        store.saveSyncDates(
            syncKey = SyncKey(FakeSyncManager.SyncKeyValue),
            serverDate = Instant.fromEpochMilliseconds(1_000L),
            localDate = Instant.fromEpochMilliseconds(7_000L),
        )
        val manager = FakeSyncManager(store = store, scope = scope)

        manager.load()

        val status = manager.currentSyncStatus
        assertTrue(status is LBSyncProcessStatus.SyncSuccessfully, "load seeds SyncSuccessfully from the persisted local date")
        assertEquals(expected = Instant.fromEpochMilliseconds(7_000L), actual = status.lastSuccessfulSync)
    }

    @Test
    fun load_keeps_never_sync_on_an_empty_store() = runManagerTest { store, scope ->
        val manager = FakeSyncManager(store = store, scope = scope)

        manager.load()

        assertEquals(expected = LBSyncProcessStatus.NeverSync, actual = manager.currentSyncStatus)
    }

    // endregion

    // region collapse-and-join

    @Test
    fun concurrent_synchronize_calls_collapse_into_one_follow_up() = runManagerTest { store, scope ->
        val firstFetch = CompletableDeferred<Unit>()
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            supportChangeNotification = true, // one fetch per run keeps the count assertion crisp
            fetchGate = firstFetch,
            gateOnlyFirstFetch = true,
        )

        // First call starts the in-flight run and parks inside its fetch. The caller is a TestScope child
        // so virtual time governs the runner's internal launches.
        val first = async { manager.synchronize() }
        advanceUntilIdle()
        assertEquals(expected = 1, actual = manager.fetchCalls, "only the in-flight run has started")

        val collapsed = List(size = 3) { async { manager.synchronize() } }
        advanceUntilIdle()
        assertEquals(expected = 1, actual = manager.fetchCalls, "collapsed callers do not start their own run")

        firstFetch.complete(Unit)
        advanceUntilIdle()

        assertTrue(first.await() is LBResult.Success)
        collapsed.forEach { assertTrue(it.await() is LBResult.Success, "every collapsed caller receives the follow-up result") }
        assertEquals(expected = 2, actual = manager.fetchCalls, "exactly one follow-up run after the in-flight run")
    }

    // endregion
}

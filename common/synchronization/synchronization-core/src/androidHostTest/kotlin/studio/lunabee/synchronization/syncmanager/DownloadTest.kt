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

import kotlinx.coroutines.test.advanceUntilIdle
import studio.lunabee.core.model.LBResult
import studio.lunabee.synchronization.store.SyncKey
import studio.lunabee.synchronization.testfixture.FakeSyncManager
import studio.lunabee.synchronization.testfixture.ServerObj
import studio.lunabee.synchronization.testfixture.runManagerTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class DownloadTest {

    private fun obj(epochMillis: Long): ServerObj = ServerObj(updatedAt = Instant.fromEpochMilliseconds(epochMillis))

    // region ascending cursor

    @Test
    fun incremental_cursor_is_the_max_updatedAt_across_all_pages() = runManagerTest { store, scope ->
        // Mixed updatedAt within and across pages; the max (700) lives mid-list on the middle page.
        val pages = listOf<FetchPage<ServerObj, Int>>(
            FetchPage(
                objects = listOf(
                    ServerObj(updatedAt = Instant.fromEpochMilliseconds(100L)),
                    ServerObj(updatedAt = Instant.fromEpochMilliseconds(300L)),
                ),
            ),
            FetchPage(
                objects = listOf(
                    ServerObj(updatedAt = Instant.fromEpochMilliseconds(700L)),
                    ServerObj(updatedAt = Instant.fromEpochMilliseconds(500L)),
                ),
            ),
            FetchPage(
                objects = listOf(ServerObj(updatedAt = Instant.fromEpochMilliseconds(200L))),
            ),
        )
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            pages = pages,
            pageSize = 2,
            supportIncremental = true,
            supportChangeNotification = true, // no re-download so the assertion is about the single download
        )

        manager.synchronize()

        assertEquals(
            expected = Instant.fromEpochMilliseconds(700L),
            actual = store.lastServerSyncDate(syncKey = manager.syncKey),
            "the persisted server cursor is the max updatedAt across every page, regardless of order",
        )
    }

    @Test
    fun terminal_cursor_is_the_running_max_when_a_timestamp_spans_uniform_pages() = runManagerTest { store, scope ->
        // page0[0,1,1] page1[1,1,1] page2[] : the max (1) is only provably closed once paging ends,
        // so the mid-paging checkpoints hold at 0 and the terminal cursor commits 1.
        val pages = listOf(
            FetchPage(objects = listOf(obj(0L), obj(1L), obj(1L)), pageInfo = 1),
            FetchPage(objects = listOf(obj(1L), obj(1L), obj(1L)), pageInfo = 1),
            FetchPage(objects = emptyList(), pageInfo = 0),
        )
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            pages = pages,
            hasNextPageOverride = { pageInfo -> pageInfo > 0 },
            supportIncremental = true,
        )

        manager.synchronize()

        assertEquals(
            expected = obj(1L).updatedAt,
            actual = store.lastServerSyncDate(syncKey = manager.syncKey),
            "a completed download commits the running max",
        )
    }

    @Test
    fun mid_paging_checkpoint_on_failure_is_the_closed_prefix_below_the_running_max() = runManagerTest { store, scope ->
        // page0[0,1,1], page1[1,1,1] then page2 throws : 1 straddles the boundary (page2 could open with more 1s),
        // so the checkpoint is 0 (the closed prefix), not 1 — the resume filter is a strict `>`.
        val pages = listOf(
            FetchPage(objects = listOf(obj(0L), obj(1L), obj(1L)), pageInfo = 1),
            FetchPage(objects = listOf(obj(1L), obj(1L), obj(1L)), pageInfo = 1),
        )
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            pages = pages,
            hasNextPageOverride = { pageInfo -> pageInfo > 0 },
            fetchErrorOnPage = 2,
            supportIncremental = true,
        )

        val result = manager.synchronize()

        assertTrue(result is LBResult.Failure, "the mid-paging fetch failure surfaces as a failed sync")
        assertEquals(
            expected = obj(0L).updatedAt,
            actual = store.lastServerSyncDate(syncKey = manager.syncKey),
            "the checkpoint is the highest updatedAt strictly below the running max, not the max itself",
        )
    }

    @Test
    fun terminal_cursor_advances_when_a_later_page_introduces_a_higher_timestamp() = runManagerTest { store, scope ->
        // page0[0,1,1] page1[1,1,1] page2[1,2] page3[] : 2 appears on page2 and paging completes,
        // so the terminal cursor is 2.
        val pages = listOf(
            FetchPage(objects = listOf(obj(0L), obj(1L), obj(1L)), pageInfo = 1),
            FetchPage(objects = listOf(obj(1L), obj(1L), obj(1L)), pageInfo = 1),
            FetchPage(objects = listOf(obj(1L), obj(2L)), pageInfo = 1),
            FetchPage(objects = emptyList(), pageInfo = 0),
        )
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            pages = pages,
            hasNextPageOverride = { pageInfo -> pageInfo > 0 },
            supportIncremental = true,
        )

        manager.synchronize()

        assertEquals(
            expected = obj(2L).updatedAt,
            actual = store.lastServerSyncDate(syncKey = manager.syncKey),
            "the terminal cursor is the running max once paging completes",
        )
    }

    // endregion

    // region sinceLastDate seeding

    @Test
    fun first_fetch_is_seeded_with_the_persisted_server_cursor() = runManagerTest { store, scope ->
        val persisted = 4_200L
        store.saveSyncDates(
            syncKey = SyncKey(FakeSyncManager.SyncKeyValue),
            serverDate = Instant.fromEpochMilliseconds(persisted),
            localDate = null,
        )
        val manager = FakeSyncManager(store = store, scope = scope, supportIncremental = true)

        manager.synchronize()

        assertEquals(
            expected = Instant.fromEpochMilliseconds(persisted),
            actual = manager.fetchArgs.first().sinceLastDate,
            "the first fetch receives the persisted server cursor as sinceLastDate",
        )
    }

    @Test
    fun first_fetch_sinceLastDate_is_null_on_an_empty_store() = runManagerTest { store, scope ->
        val manager = FakeSyncManager(store = store, scope = scope, supportIncremental = true)

        manager.synchronize()

        assertNull(manager.fetchArgs.first().sinceLastDate, "a virgin store seeds the first fetch with a null cursor")
    }

    // endregion

    // region paging cursor threading

    @Test
    fun each_page_fetch_receives_the_previous_pages_nextCursor() = runManagerTest { store, scope ->
        val pages = listOf(
            FetchPage<ServerObj, Int>(
                objects = List(size = 2) { ServerObj(updatedAt = Instant.fromEpochMilliseconds(it + 1L)) },
                nextCursor = "cursor-1",
            ),
            FetchPage<ServerObj, Int>(
                objects = List(size = 2) { ServerObj(updatedAt = Instant.fromEpochMilliseconds(it + 10L)) },
                nextCursor = "cursor-2",
            ),
            FetchPage<ServerObj, Int>(
                objects = listOf(ServerObj(updatedAt = Instant.fromEpochMilliseconds(100L))),
            ),
        )
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            pages = pages,
            pageSize = 2,
            supportChangeNotification = true,
        )

        manager.synchronize()

        assertEquals(
            expected = listOf(null, "cursor-1", "cursor-2"),
            actual = manager.fetchArgs.map { it.cursor },
            "each subsequent fetch is threaded the previous page's nextCursor (first page null)",
        )
    }

    // endregion

    // region hasNextPage decision

    @Test
    fun hasNextPage_pageInfo_override_decides_paging() = runManagerTest { store, scope ->
        val pages = listOf(
            FetchPage(objects = listOf(ServerObj(updatedAt = null)), pageInfo = 1),
            FetchPage(objects = listOf(ServerObj(updatedAt = null)), pageInfo = 0),
        )
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            pages = pages,
            // queryPageSize would say "stop" (1 != null) but pageInfo overrides it and forces a second page.
            hasNextPageOverride = { pageInfo -> pageInfo > 0 },
            supportChangeNotification = true,
        )

        manager.synchronize()

        assertEquals(
            expected = 2,
            actual = manager.fetchCalls,
            "with pageInfo present the override decides paging, not the object-count heuristic",
        )
    }

    @Test
    fun hasNextPage_falls_back_to_queryPageSize_when_pageInfo_is_absent() = runManagerTest { store, scope ->
        // No pageInfo on any page: the engine compares objectCount to queryPageSize (2). A full page (2)
        // continues; a short page (1) stops.
        val pages = listOf(
            FetchPage(objects = List(size = 2) { ServerObj(updatedAt = null) }),
            FetchPage<ServerObj, Int>(objects = listOf(ServerObj(updatedAt = null))),
        )
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            pages = pages,
            pageSize = 2,
            supportChangeNotification = true,
        )

        manager.synchronize()

        assertEquals(
            expected = 2,
            actual = manager.fetchCalls,
            "objectCount == queryPageSize continues; a short page stops the loop",
        )
    }

    // endregion

    // region empty page

    @Test
    fun empty_first_page_does_one_fetch_and_finishes_successfully() = runManagerTest { store, scope ->
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            pages = listOf(FetchPage(objects = emptyList())),
            supportChangeNotification = true,
        )
        val statuses = manager.recordStatuses(scope, testScheduler)

        val result = manager.synchronize()
        advanceUntilIdle()

        assertTrue(result is LBResult.Success, "an empty download still succeeds")
        assertEquals(expected = 1, actual = manager.fetchCalls, "an empty first page does exactly one fetch")
        assertTrue(
            statuses().contains("DownloadFinishSuccessfully"),
            "the empty download reaches DownloadFinishSuccessfully",
        )
    }

    // endregion

    // region per-page processed count

    @Test
    fun download_updated_processed_counts_match_each_page_size_in_order() = runManagerTest { store, scope ->
        val pages = listOf(
            FetchPage(objects = List(size = 3) { ServerObj(updatedAt = Instant.fromEpochMilliseconds(it + 1L)) }),
            FetchPage(objects = List(size = 3) { ServerObj(updatedAt = Instant.fromEpochMilliseconds(it + 10L)) }),
            FetchPage<ServerObj, Int>(objects = listOf(ServerObj(updatedAt = Instant.fromEpochMilliseconds(100L)))),
        )
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            pages = pages,
            pageSize = 3,
            supportChangeNotification = true,
        )

        manager.synchronize()

        // updatedPageSizes records each page handed to updateData, which the engine then reports as the
        // DownloadUpdated.processedObjectCount for that page.
        assertEquals(
            expected = listOf(3, 3, 1),
            actual = manager.updatedPageSizes,
            "each DownloadUpdated carries its own page's object count, in order",
        )
    }

    // endregion

    // region terminal cursor & local-date persistence

    @Test
    fun terminal_success_persists_both_local_date_and_server_cursor_even_without_incremental() = runManagerTest { store, scope ->
        val serverMax = Instant.fromEpochMilliseconds(9_000L)
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            pages = listOf(FetchPage<ServerObj, Int>(objects = listOf(ServerObj(updatedAt = serverMax)))),
            supportIncremental = false,
            supportChangeNotification = true,
        )

        assertNull(manager.lastSuccessfulSyncDate(), "no local date before the first sync")

        manager.synchronize()

        assertTrue(
            manager.lastSuccessfulSyncDate() != null,
            "the local sync date is always persisted on terminal download success",
        )
        assertEquals(
            expected = serverMax,
            actual = store.lastServerSyncDate(syncKey = manager.syncKey),
            "the server cursor is also persisted on terminal success, even with incremental sync off (legacy parity)",
        )
    }

    @Test
    fun mid_paging_failure_does_not_stamp_the_local_sync_date() = runManagerTest { store, scope ->
        // A checkpoint persists the server cursor mid-paging, but the local "last successful sync" marker
        // must stay unset until the whole download completes — a mid-paging failure is not a success.
        val pages = listOf(
            FetchPage(objects = listOf(obj(0L), obj(1L), obj(1L)), pageInfo = 1),
        )
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            pages = pages,
            hasNextPageOverride = { pageInfo -> pageInfo > 0 },
            fetchErrorOnPage = 1,
            supportIncremental = true,
        )

        val result = manager.synchronize()

        assertTrue(result is LBResult.Failure, "the mid-paging fetch failure surfaces as a failed sync")
        assertEquals(
            expected = obj(0L).updatedAt,
            actual = store.lastServerSyncDate(syncKey = manager.syncKey),
            "the server cursor is still checkpointed mid-paging",
        )
        assertNull(
            manager.lastSuccessfulSyncDate(),
            "a partial download does not stamp the local last-successful-sync date",
        )
    }

    // endregion
}

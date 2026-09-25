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

package studio.lunabee.synchronization.remote

import kotlinx.coroutines.test.runTest
import studio.lunabee.core.model.LBResult
import studio.lunabee.synchronization.LBSyncOperator
import studio.lunabee.synchronization.store.SyncKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class LBRemotePullSyncManagerTest {
    private val cursorStore = FakeSyncTimestampLocalDataSource()
    private val localDataSource = FakeLocalDataSource()

    @Test
    fun first_download_reads_every_page_from_the_start() = runTest {
        val remote = FakeRemoteDataSource(pages = ThreePages)

        LBSyncOperator.sync(manager = manager(remote))

        assertEquals(
            expected = listOf(
                FetchCall(page = 0, updatedAfter = null),
                FetchCall(page = 1, updatedAfter = null),
                FetchCall(page = 2, updatedAfter = null),
            ),
            actual = remote.fetchCalls,
        )
    }

    @Test
    fun a_download_is_saved_in_a_single_call_once_every_page_is_read() = runTest {
        LBSyncOperator.sync(manager = manager(FakeRemoteDataSource(pages = ThreePages)))

        assertEquals(expected = listOf(listOf(Item("a"), Item("b"), Item("c"))), actual = localDataSource.savedDownloads)
    }

    @Test
    fun the_cursor_is_the_newest_update_read_left_out_records_included() = runTest {
        val manager = manager(FakeRemoteDataSource(pages = ThreePages))

        LBSyncOperator.sync(manager = manager)

        assertEquals(expected = at(seconds = 40), actual = cursorStore.lastServerSyncDate(syncKey = manager.syncKey))
    }

    @Test
    fun the_next_download_reads_from_the_cursor() = runTest {
        val remote = FakeRemoteDataSource()
        val manager = manager(remote)
        cursorStore.saveSyncDates(syncKey = manager.syncKey, serverDate = at(seconds = 40), localDate = null)

        LBSyncOperator.sync(manager = manager)

        assertEquals(expected = listOf(FetchCall(page = 0, updatedAfter = at(seconds = 40))), actual = remote.fetchCalls)
    }

    @Test
    fun an_empty_download_keeps_the_cursor_and_saves_nothing() = runTest {
        val manager = manager(FakeRemoteDataSource())
        cursorStore.saveSyncDates(syncKey = manager.syncKey, serverDate = at(seconds = 40), localDate = null)

        LBSyncOperator.sync(manager = manager)

        assertEquals(expected = at(seconds = 40), actual = cursorStore.lastServerSyncDate(syncKey = manager.syncKey))
        assertTrue(localDataSource.savedDownloads.isEmpty(), "nothing is saved")
    }

    @Test
    fun a_failure_while_paging_saves_nothing_and_keeps_the_cursor() = runTest {
        val manager = manager(FakeRemoteDataSource(pages = ThreePages, failingPage = 2))

        val result = LBSyncOperator.sync(manager = manager)

        assertTrue(result is LBResult.Failure, "the run fails")
        assertTrue(localDataSource.savedDownloads.isEmpty(), "the pages already read are not saved")
        assertNull(cursorStore.lastServerSyncDate(syncKey = manager.syncKey), "the cursor did not move")
    }

    @Test
    fun a_download_after_a_failed_one_saves_only_its_own_pages() = runTest {
        LBSyncOperator.sync(manager = manager(FakeRemoteDataSource(pages = ThreePages, failingPage = 2)))

        LBSyncOperator.sync(manager = manager(FakeRemoteDataSource(pages = ThreePages)))

        assertEquals(expected = listOf(listOf(Item("a"), Item("b"), Item("c"))), actual = localDataSource.savedDownloads)
    }

    private fun manager(remote: FakeRemoteDataSource): LBRemotePullSyncManager<Item> = LBRemotePullSyncManager<Item>(
        syncKey = SyncKey("items"),
        remoteDataSource = remote,
        localDataSource = localDataSource,
    ).apply { retryTempo = null }

    private companion object {
        fun at(seconds: Long): Instant = Instant.fromEpochSeconds(seconds)

        val ThreePages: List<LBRemotePage<Item>> = listOf(
            LBRemotePage(objects = listOf(Item("a")), maxUpdatedAt = at(seconds = 10), isLastPage = false),
            LBRemotePage(objects = listOf(Item("b")), maxUpdatedAt = at(seconds = 40), isLastPage = false),
            LBRemotePage(objects = listOf(Item("c")), maxUpdatedAt = at(seconds = 30), isLastPage = true),
        )
    }
}

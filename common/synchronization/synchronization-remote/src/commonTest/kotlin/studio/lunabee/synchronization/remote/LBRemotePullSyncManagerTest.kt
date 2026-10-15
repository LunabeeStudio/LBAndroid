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
    fun first_download_reads_every_page_from_the_previous_page_cursor() = runTest {
        val remote = FakeRemoteDataSource(pages = ThreePages)

        LBSyncOperator.sync(manager = manager(remote))

        assertEquals(
            expected = listOf(
                FetchCall(cursor = null, updatedAfter = null),
                FetchCall(cursor = "after-a", updatedAfter = null),
                FetchCall(cursor = "after-b", updatedAfter = null),
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

        assertEquals(expected = listOf(FetchCall(cursor = null, updatedAfter = at(seconds = 40))), actual = remote.fetchCalls)
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
        val manager = manager(FakeRemoteDataSource(pages = ThreePages, failingCursor = "after-b"))

        val result = LBSyncOperator.sync(manager = manager)

        assertTrue(result is LBResult.Failure, "the run fails")
        assertTrue(localDataSource.savedDownloads.isEmpty(), "the pages already read are not saved")
        assertNull(cursorStore.lastServerSyncDate(syncKey = manager.syncKey), "the cursor did not move")
    }

    @Test
    fun a_download_after_a_failed_one_saves_only_its_own_pages() = runTest {
        LBSyncOperator.sync(manager = manager(FakeRemoteDataSource(pages = ThreePages, failingCursor = "after-b")))

        LBSyncOperator.sync(manager = manager(FakeRemoteDataSource(pages = ThreePages)))

        assertEquals(expected = listOf(listOf(Item("a"), Item("b"), Item("c"))), actual = localDataSource.savedDownloads)
    }

    @Test
    fun a_record_updated_mid_download_is_not_lost() = runTest {
        val remote = FakeKeysetRemoteDataSource(pageSize = 2)
        listOf("a", "b", "c", "d").forEachIndexed { index, id -> remote.put(item = Item(id), updatedAt = at(seconds = 10L * (index + 1))) }
        remote.onPageServed = {
            remote.put(item = Item("a", value = 1), updatedAt = at(seconds = 50))
            remote.onPageServed = null
        }
        val manager = manager(remote)

        LBSyncOperator.sync(manager = manager)

        assertEquals(
            expected = listOf(listOf(Item("a"), Item("b"), Item("c"), Item("d"), Item("a", value = 1))),
            actual = localDataSource.savedDownloads,
        )
        assertEquals(expected = at(seconds = 50), actual = cursorStore.lastServerSyncDate(syncKey = manager.syncKey))
    }

    private fun manager(remote: LBPullRemoteDataSource<Item>): LBRemotePullSyncManager<Item> = LBRemotePullSyncManager<Item>(
        syncKey = SyncKey("items"),
        remoteDataSource = remote,
        localDataSource = localDataSource,
    ).apply { retryTempo = null }

    private companion object {
        fun at(seconds: Long): Instant = Instant.fromEpochSeconds(seconds)

        val ThreePages: List<LBRemotePage<Item>> = listOf(
            LBRemotePage(objects = listOf(Item("a")), maxUpdatedAt = at(seconds = 10), nextCursor = "after-a"),
            LBRemotePage(objects = listOf(Item("b")), maxUpdatedAt = at(seconds = 40), nextCursor = "after-b"),
            LBRemotePage(objects = listOf(Item("c")), maxUpdatedAt = at(seconds = 30), nextCursor = null),
        )
    }
}

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
import studio.lunabee.synchronization.store.LBSyncStorage
import studio.lunabee.synchronization.store.SyncKey
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LBRemoteSyncManagerTest {
    @BeforeTest
    fun installCursorStore() {
        LBSyncStorage.install(store = FakeSyncTimestampLocalDataSource())
    }

    @Test
    fun an_object_unknown_to_the_backend_is_created_then_marked_pushed() = runTest {
        val remote = FakeRemoteDataSource()
        val local = FakeLocalDataSource(toPush = listOf(Item("a")))

        LBSyncOperator.sync(manager = manager(remote = remote, local = local))

        assertEquals(expected = listOf("find a", "create a"), actual = remote.calls.filterNot { call -> call.startsWith("fetch") })
        assertEquals(expected = listOf(Item("a")), actual = local.pushed)
    }

    @Test
    fun an_object_known_to_the_backend_is_updated_by_its_backend_id() = runTest {
        val remote = FakeRemoteDataSource(serverIds = mapOf("a" to "server-a"))

        LBSyncOperator.sync(manager = manager(remote = remote, local = FakeLocalDataSource(toPush = listOf(Item("a")))))

        assertEquals(expected = listOf("find a", "update server-a"), actual = remote.calls.filterNot { call -> call.startsWith("fetch") })
    }

    @Test
    fun an_upload_failure_stops_the_upload_and_keeps_the_rest_to_push() = runTest {
        val remote = FakeRemoteDataSource(failingPushId = "b")
        val local = FakeLocalDataSource(toPush = listOf(Item("a"), Item("b"), Item("c")))

        val result = LBSyncOperator.sync(manager = manager(remote = remote, local = local))

        assertTrue(result is LBResult.Failure, "the run fails")
        assertEquals(expected = listOf(Item("a")), actual = local.pushed)
        assertEquals(expected = listOf(Item("b"), Item("c")), actual = local.objectsToPush())
    }

    @Test
    fun push_before_pull_uploads_then_downloads_once() = runTest {
        val remote = FakeRemoteDataSource()

        LBSyncOperator.sync(
            manager = manager(remote = remote, local = FakeLocalDataSource(toPush = listOf(Item("a"))), pushBeforePull = true),
        )

        assertEquals(expected = listOf("find a", "create a", "fetch 0"), actual = remote.calls)
    }

    @Test
    fun push_before_pull_skips_the_download_after_an_upload_failure() = runTest {
        val remote = FakeRemoteDataSource(failingPushId = "a")

        LBSyncOperator.sync(
            manager = manager(remote = remote, local = FakeLocalDataSource(toPush = listOf(Item("a"))), pushBeforePull = true),
        )

        assertEquals(expected = listOf("find a", "create a"), actual = remote.calls)
    }

    @Test
    fun the_default_order_downloads_around_the_upload() = runTest {
        val remote = FakeRemoteDataSource()

        LBSyncOperator.sync(manager = manager(remote = remote, local = FakeLocalDataSource(toPush = listOf(Item("a")))))

        assertEquals(expected = listOf("fetch 0", "find a", "create a", "fetch 0"), actual = remote.calls)
    }

    private fun manager(
        remote: FakeRemoteDataSource,
        local: FakeLocalDataSource,
        pushBeforePull: Boolean = false,
    ): LBRemoteSyncManager<Item> = LBRemoteSyncManager(
        syncKey = SyncKey("items"),
        remoteDataSource = remote,
        localDataSource = local,
        pushBeforePull = pushBeforePull,
    ).apply { retryTempo = null }
}

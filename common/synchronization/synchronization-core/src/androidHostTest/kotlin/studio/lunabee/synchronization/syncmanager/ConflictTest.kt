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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import studio.lunabee.core.model.LBResult
import studio.lunabee.synchronization.testfixture.FakeSyncManager
import studio.lunabee.synchronization.testfixture.LocalObj
import studio.lunabee.synchronization.testfixture.ServerObj
import studio.lunabee.synchronization.testfixture.StatefulFakeSyncManager
import studio.lunabee.synchronization.testfixture.runManagerTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Conflict resolution. The engine is **last-write-wins by `updatedAt` at the row level — there is NO
 * field-level merge**: the download phase upserts every server row first, so a server row that arrives
 * for a locally-pending id overwrites the local edit and marks it in-sync (it is therefore NOT pushed
 * back). Only rows the server did not return remain pending and are uploaded.
 *
 * These tests model that with [StatefulFakeSyncManager]'s in-memory dao.
 */
class ConflictTest {

    // region download-before-upload last-write-wins

    @Test
    fun a_server_update_for_a_locally_pending_id_wins_over_the_local_edit() = runManagerTest { store, scope ->
        // r1: a local edit (pending) that the server ALSO updated → conflict. The server's r1' is newer.
        // r2: a local-only pending edit the server did not touch → it must still be pushed.
        val serverR1 = ServerObj(id = "r1", updatedAt = Instant.fromEpochMilliseconds(2_000L))
        val manager = StatefulFakeSyncManager(
            store = store,
            scope = scope,
            downloadPages = listOf(FetchPage<ServerObj, Int>(objects = listOf(serverR1))),
        )
        manager.seedLocalDirty(ServerObj(id = "r1", updatedAt = Instant.fromEpochMilliseconds(1_000L)))
        manager.seedLocalDirty(ServerObj(id = "r2", updatedAt = Instant.fromEpochMilliseconds(1_000L)))

        val result = manager.synchronize()
        advanceUntilIdle()

        assertTrue(result is LBResult.Success, "the conflict sync completes successfully")

        // updateData (download upsert) ran before pushObjectsToServer (upload).
        val updateIndex = manager.callLog.indexOf("update")
        val pushIndex = manager.callLog.indexOf("push")
        assertTrue(updateIndex >= 0, "the server row was upserted via updateData")
        assertTrue(pushIndex >= 0, "the remaining pending row was pushed via pushObjectsToServer")
        assertTrue(updateIndex < pushIndex, "download (updateData) precedes upload (pushObjectsToServer)")

        // Last-write-wins: r1 now holds the server's value; the local edit was discarded.
        assertEquals(
            expected = serverR1,
            actual = manager.dao["r1"],
            "the server's r1' overwrote the local edit (last-write-wins, no merge)",
        )

        // The conflicting r1 was NOT pushed (the download marked it in-sync); only r2 was uploaded.
        assertEquals(
            expected = listOf(listOf(LocalObj("r2"))),
            actual = manager.pushedBatches,
            "only the row the server did not return is pushed; the overwritten conflict id is not",
        )
    }

    // endregion

    // region collapsed callers receive the follow-up result

    @Test
    fun collapsed_concurrent_callers_all_receive_the_single_follow_up_result() = runManagerTest { store, scope ->
        val firstFetch = CompletableDeferred<Unit>()
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            supportChangeNotification = true, // one fetch per run keeps the count assertion crisp
            fetchGate = firstFetch,
            gateOnlyFirstFetch = true,
        )

        // The in-flight run parks inside its first fetch.
        val first: Deferred<LBResult<Unit>> = async { manager.synchronize() }
        advanceUntilIdle()

        // Two callers arrive while the run is in flight: they collapse onto ONE shared follow-up.
        val a: Deferred<LBResult<Unit>> = async { manager.synchronize() }
        val b: Deferred<LBResult<Unit>> = async { manager.synchronize() }
        advanceUntilIdle()

        firstFetch.complete(Unit) // release the in-flight run; the single follow-up then runs
        advanceUntilIdle()

        first.await()
        val resultA = a.await()
        val resultB = b.await()

        // Both collapsed callers observe the SAME follow-up run's result (a distinct run from the first).
        assertTrue(resultA is LBResult.Success)
        assertTrue(resultB is LBResult.Success)
        assertEquals(expected = resultA, actual = resultB, "both collapsed callers receive the same follow-up result")
        assertEquals(
            expected = 2,
            actual = manager.fetchCalls,
            "the block ran exactly twice: the in-flight run and one collapsed follow-up",
        )
    }

    // endregion

    // region pending upload survives a retry

    @Test
    fun a_pending_local_upload_is_still_pushed_on_the_retry() = runManagerTest { store, scope ->
        val manager = StatefulFakeSyncManager(
            store = store,
            scope = scope,
            downloadPages = listOf(FetchPage<ServerObj, Int>(objects = emptyList())),
            retryTempo = 30.seconds,
        )
        manager.seedLocalDirty(ServerObj(id = "p1", updatedAt = Instant.fromEpochMilliseconds(1_000L)))
        // Fail the first push so the run fails and a retry is scheduled.
        manager.failNextPush()

        // runCurrent settles the first (failing) run at the current instant while the +30s retry stays
        // parked, so the first-attempt assertion sees exactly one push.
        val caller: Deferred<LBResult<Unit>> = async { manager.synchronize() }
        runCurrent()
        assertTrue(caller.await() is LBResult.Failure, "the first run fails on the push")
        assertEquals(expected = listOf(listOf(LocalObj("p1"))), actual = manager.pushedBatches, "the first attempt tried p1")

        // The retry fires after the tempo; the pending p1 must still be there to push. The retry succeeds
        // (the transient failure cleared) so advanceUntilIdle drains it without a perpetual loop.
        advanceTimeBy(30.seconds)
        advanceUntilIdle()

        assertEquals(
            expected = listOf(listOf(LocalObj("p1")), listOf(LocalObj("p1"))),
            actual = manager.pushedBatches,
            "the retry re-pushes the still-pending local upload (it was not lost by the failed run)",
        )
        assertTrue(
            manager.currentSyncStatus is LBSyncProcessStatus.SyncSuccessfully,
            "the retry succeeds once the transient push failure clears",
        )
    }

    // endregion
}

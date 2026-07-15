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

package studio.lunabee.synchronization

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import studio.lunabee.core.model.LBResult
import studio.lunabee.synchronization.store.SyncKey
import studio.lunabee.synchronization.store.SyncTimestampStore
import studio.lunabee.synchronization.syncmanager.FetchPage
import studio.lunabee.synchronization.syncmanager.LBSyncManager
import studio.lunabee.synchronization.syncmanager.LBSyncProcessStatus
import studio.lunabee.synchronization.testfixture.freshStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

class LBSyncGroupTest {

    // region all managers attempt despite a failing sibling

    @Test
    fun every_manager_runs_to_completion_even_when_a_sibling_fails() = runGroupTest { store, scope ->
        val ok1 = FakeGroupManager(store = store, scope = scope, syncKey = "ok1")
        val failing = FakeGroupManager(store = store, scope = scope, syncKey = "boom", fetchError = IllegalStateException("boom"))
        val ok2 = FakeGroupManager(store = store, scope = scope, syncKey = "ok2")
        val group = LBSyncGroup(syncManagers = linkedSetOf(ok1, failing, ok2))

        group.syncManagers()

        // No fail-fast sibling cancellation: every manager attempted (its fetch ran) and reached a
        // terminal status (success or error), never left mid-pipeline.
        assertEquals(expected = 1, actual = ok1.fetchCalls, "the first healthy manager ran")
        assertEquals(expected = 1, actual = failing.fetchCalls, "the failing manager ran")
        assertEquals(expected = 1, actual = ok2.fetchCalls, "the manager after the failure still ran")
        assertTrue(ok1.currentSyncStatus is LBSyncProcessStatus.SyncSuccessfully)
        assertTrue(failing.currentSyncStatus is LBSyncProcessStatus.DownloadFinishWithError)
        assertTrue(ok2.currentSyncStatus is LBSyncProcessStatus.SyncSuccessfully)
    }

    // endregion

    // region result shapes

    @Test
    fun no_failure_returns_success() = runGroupTest { store, scope ->
        val group = LBSyncGroup(
            syncManagers = linkedSetOf(
                FakeGroupManager(store = store, scope = scope, syncKey = "ok1"),
                FakeGroupManager(store = store, scope = scope, syncKey = "ok2"),
            ),
        )

        assertTrue(group.syncManagers() is LBResult.Success, "all managers succeeding returns Success")
    }

    @Test
    fun exactly_one_failure_returns_that_managers_error() = runGroupTest { store, scope ->
        val boom = IllegalStateException("single boom")
        val group = LBSyncGroup(
            syncManagers = linkedSetOf(
                FakeGroupManager(store = store, scope = scope, syncKey = "ok"),
                FakeGroupManager(store = store, scope = scope, syncKey = "boom", fetchError = boom),
            ),
        )

        val result = group.syncManagers()

        assertTrue(result is LBResult.Failure, "one failing manager returns Failure")
        assertSame(boom, result.throwable, "a single failure surfaces that manager's own error, not an aggregate")
    }

    @Test
    fun several_failures_return_an_aggregate_exposing_every_error() = runGroupTest { store, scope ->
        val boom1 = IllegalStateException("boom1")
        val boom2 = IllegalArgumentException("boom2")
        val group = LBSyncGroup(
            syncManagers = linkedSetOf(
                FakeGroupManager(store = store, scope = scope, syncKey = "boom1", fetchError = boom1),
                FakeGroupManager(store = store, scope = scope, syncKey = "ok"),
                FakeGroupManager(store = store, scope = scope, syncKey = "boom2", fetchError = boom2),
            ),
        )

        val result = group.syncManagers()

        assertTrue(result is LBResult.Failure, "several failing managers return Failure")
        val aggregate = result.throwable
        assertTrue(aggregate is LBSyncAggregateException, "several failures are wrapped in an aggregate exception")
        assertEquals(
            expected = setOf(boom1, boom2),
            actual = aggregate.errors.toSet(),
            "the aggregate exposes every underlying error",
        )
    }

    // endregion

    // region disabled gate

    @Test
    fun disabled_gate_marks_every_manager_disabled_and_fails_with_closure_exception() = runGroupTest { store, scope ->
        val m1 = FakeGroupManager(store = store, scope = scope, syncKey = "m1")
        val m2 = FakeGroupManager(store = store, scope = scope, syncKey = "m2")
        val group = LBSyncGroup(syncManagers = linkedSetOf(m1, m2)).apply { isEnabled = { false } }

        val result = group.syncManagers()

        assertTrue(result is LBResult.Failure, "a disabled group fails")
        assertTrue(result.throwable is LBSyncClosureException, "a disabled group fails with LBSyncClosureException")
        assertEquals(expected = LBSyncProcessStatus.Disabled, actual = m1.currentSyncStatus)
        assertEquals(expected = LBSyncProcessStatus.Disabled, actual = m2.currentSyncStatus)
        // The gate short-circuits before any manager runs.
        assertEquals(expected = 0, actual = m1.fetchCalls, "a disabled group does not run its managers")
        assertEquals(expected = 0, actual = m2.fetchCalls)
    }

    // endregion

    // region gate evaluated exactly once

    @Test
    fun gate_is_evaluated_exactly_once_per_attempt() = runGroupTest { store, scope ->
        var gateCalls = 0
        val group = LBSyncGroup(
            syncManagers = linkedSetOf(
                FakeGroupManager(store = store, scope = scope, syncKey = "m1"),
                FakeGroupManager(store = store, scope = scope, syncKey = "m2"),
            ),
        ).apply {
            isEnabled = {
                gateCalls++
                true
            }
        }

        group.syncManagers()

        assertEquals(expected = 1, actual = gateCalls, "the gate is evaluated exactly once per sync attempt")
    }

    // endregion

    // region test infrastructure

    private fun runGroupTest(body: suspend TestScope.(store: SyncTimestampStore, scope: CoroutineScope) -> Unit) = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        try {
            body(freshStore(), scope)
        } finally {
            scope.cancel()
        }
    }
}

private data class GroupServerObj(val updatedAt: Instant?)

private data class GroupLocalObj(val id: String)

/**
 * Configurable fake [LBSyncManager] whose SPI deterministically ends in success, or in a download
 * failure when [fetchError] is set, so group result/status aggregation can be asserted on observable
 * outcomes only. Automatic retry is disabled so a failure resolves immediately under virtual time.
 */
private class FakeGroupManager(
    store: SyncTimestampStore,
    scope: CoroutineScope,
    syncKey: String,
    private val fetchError: Exception? = null,
) : LBSyncManager<GroupServerObj, GroupLocalObj, Nothing>(providedTimestampStore = store, scope = scope) {

    init {
        retryTempo = null
    }

    var fetchCalls: Int = 0
        private set

    override val syncKey: SyncKey = SyncKey(syncKey)

    override suspend fun clearData() = Unit

    override suspend fun updateData(data: List<GroupServerObj>) = Unit

    override suspend fun fetchRequest(page: Int, cursor: String?, sinceLastDate: Instant?): FetchPage<GroupServerObj, Nothing> {
        fetchCalls += 1
        fetchError?.let { throw it }
        return FetchPage(objects = emptyList())
    }

    override fun updatedAt(obj: GroupServerObj): Instant? = obj.updatedAt

    override fun isInSync(obj: GroupLocalObj): Boolean = true

    override suspend fun objectToBeUploaded(): List<GroupLocalObj> = emptyList()

    override suspend fun pushObjectsToServer(objects: List<GroupLocalObj>) = Unit

    override suspend fun hasSomethingToUpload(): Boolean = false
}

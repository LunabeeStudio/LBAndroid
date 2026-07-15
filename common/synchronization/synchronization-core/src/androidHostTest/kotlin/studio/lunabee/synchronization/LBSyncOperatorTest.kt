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
import studio.lunabee.synchronization.syncmanager.LBSyncRefreshEvent
import studio.lunabee.synchronization.testfixture.freshStore
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class LBSyncOperatorTest {

    @BeforeTest
    fun clearGroups() {
        LBSyncOperator.groups.clear()
    }

    // region sequential group order

    @Test
    fun groups_run_sequentially_in_registration_order() = runOperatorTest { store, scope ->
        val order = mutableListOf<String>()
        register("first", group(store, scope, "a", order = order, id = "first"))
        register("second", group(store, scope, "b", order = order, id = "second"))
        register("third", group(store, scope, "c", order = order, id = "third"))

        val result = LBSyncOperator.syncAllManagers()

        assertTrue(result is LBResult.Success, "all groups succeeding returns Success")
        assertEquals(
            expected = listOf("first", "second", "third"),
            actual = order,
            "groups complete in LinkedHashMap registration order",
        )
    }

    // endregion

    // region run-all with aggregation

    @Test
    fun an_early_group_failure_no_longer_swallows_and_every_group_runs() = runOperatorTest { store, scope ->
        val order = mutableListOf<String>()
        val boom1 = IllegalStateException("early boom")
        register("early", group(store, scope, "early", order = order, id = "early", fetchError = boom1))
        register("late", group(store, scope, "late", order = order, id = "late"))

        val result = LBSyncOperator.syncAllManagers()

        assertEquals(
            expected = listOf("early", "late"),
            actual = order,
            "the late group still ran after the early group failed (no short-circuit)",
        )
        assertTrue(result is LBResult.Failure, "a failing group surfaces as Failure")
        assertSame(boom1, result.throwable, "a single failure surfaces that group's own error, not an aggregate")
    }

    @Test
    fun several_group_failures_are_aggregated_exposing_every_error() = runOperatorTest { store, scope ->
        val order = mutableListOf<String>()
        val boom1 = IllegalStateException("boom1")
        val boom2 = IllegalArgumentException("boom2")
        register("g1", group(store, scope, "g1", order = order, id = "g1", fetchError = boom1))
        register("g2", group(store, scope, "g2", order = order, id = "g2"))
        register("g3", group(store, scope, "g3", order = order, id = "g3", fetchError = boom2))

        val result = LBSyncOperator.syncAllManagers()

        assertEquals(expected = listOf("g1", "g2", "g3"), actual = order, "every group ran")
        assertTrue(result is LBResult.Failure, "several failing groups return Failure")
        val aggregate = result.throwable
        assertTrue(aggregate is LBSyncAggregateException, "several group failures are wrapped in an aggregate exception")
        assertEquals(
            expected = listOf(boom1, boom2),
            actual = aggregate.errors,
            "the aggregate exposes every underlying group error, in registration order (group-level throwables, not flattened)",
        )
    }

    // endregion

    // region refresh-event debounce filtering

    @Test
    fun groups_for_event_excludes_a_group_whose_delay_has_not_elapsed_and_includes_one_whose_delay_has() =
        runOperatorTest { store, scope ->
            val fresh = FakeOperatorManager(store = store, scope = scope, syncKey = "fresh")
            // A recent successful sync: the one-hour debounce has NOT elapsed → excluded.
            fresh.setStatusInternal(LBSyncProcessStatus.SyncSuccessfully(Clock.System.now()))
            val freshGroup = LBSyncGroup(
                syncManagers = linkedSetOf(fresh),
                refreshEvents = listOf(LBSyncRefreshEvent.AppForeground(minimumDelay = 1.hours)),
            )

            val stale = FakeOperatorManager(store = store, scope = scope, syncKey = "stale")
            // A long-past successful sync: the one-hour debounce HAS elapsed → included.
            stale.setStatusInternal(LBSyncProcessStatus.SyncSuccessfully(Instant.fromEpochMilliseconds(0L)))
            val staleGroup = LBSyncGroup(
                syncManagers = linkedSetOf(stale),
                refreshEvents = listOf(LBSyncRefreshEvent.AppForeground(minimumDelay = 1.hours)),
            )

            register("fresh", freshGroup)
            register("stale", staleGroup)

            val matched = LBSyncOperator.groupsForEvent(LBSyncRefreshEvent.AppForeground::class)

            assertEquals(expected = listOf(staleGroup), actual = matched, "only the stale group (delay elapsed) matches")
        }

    @Test
    fun groups_for_event_ignores_groups_carrying_a_different_event_type() = runOperatorTest { store, scope ->
        val stale = FakeOperatorManager(store = store, scope = scope, syncKey = "stale")
        stale.setStatusInternal(LBSyncProcessStatus.SyncSuccessfully(Instant.fromEpochMilliseconds(0L)))
        // Carries InternetIsBack with an elapsed delay, but we query for AppForeground.
        val internetGroup = LBSyncGroup(
            syncManagers = linkedSetOf(stale),
            refreshEvents = listOf(LBSyncRefreshEvent.InternetIsBack(minimumDelay = 1.hours)),
        )
        register("internet", internetGroup)

        val matched = LBSyncOperator.groupsForEvent(LBSyncRefreshEvent.AppForeground::class)

        assertTrue(matched.isEmpty(), "a group carrying only a different event type does not match")
    }

    // endregion

    // region test infrastructure

    private fun register(key: String, group: LBSyncGroup) {
        LBSyncOperator.groups[key] = group
    }

    private fun group(
        store: SyncTimestampStore,
        scope: CoroutineScope,
        syncKey: String,
        order: MutableList<String>,
        id: String,
        fetchError: Exception? = null,
    ): LBSyncGroup = LBSyncGroup(
        syncManagers = linkedSetOf(
            FakeOperatorManager(store = store, scope = scope, syncKey = syncKey, runOrder = order, runId = id, fetchError = fetchError),
        ),
    )

    private fun runOperatorTest(body: suspend TestScope.(store: SyncTimestampStore, scope: CoroutineScope) -> Unit) = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        try {
            body(freshStore(), scope)
        } finally {
            scope.cancel()
        }
    }
}

private data class OperatorServerObj(val updatedAt: Instant?)

private data class OperatorLocalObj(val id: String)

/**
 * Configurable fake [LBSyncManager] for operator-level assertions. When [runOrder]/[runId] are set, it
 * appends [runId] to [runOrder] as its `fetchRequest` runs, so sequential group order can be observed.
 * Setting [fetchError] makes the run fail (download error). Automatic retry is disabled so a failure
 * resolves immediately under virtual time.
 */
private class FakeOperatorManager(
    store: SyncTimestampStore,
    scope: CoroutineScope,
    syncKey: String,
    private val runOrder: MutableList<String>? = null,
    private val runId: String? = null,
    private val fetchError: Exception? = null,
) : LBSyncManager<OperatorServerObj, OperatorLocalObj, Nothing>(providedTimestampStore = store, scope = scope) {

    init {
        retryTempo = null
    }

    override val syncKey: SyncKey = SyncKey(syncKey)

    override suspend fun clearData() = Unit

    override suspend fun updateData(data: List<OperatorServerObj>) = Unit

    override suspend fun fetchRequest(page: Int, cursor: String?, sinceLastDate: Instant?): FetchPage<OperatorServerObj, Nothing> {
        runId?.let { runOrder?.add(it) }
        fetchError?.let { throw it }
        return FetchPage(objects = emptyList())
    }

    override fun updatedAt(obj: OperatorServerObj): Instant? = obj.updatedAt

    override fun isInSync(obj: OperatorLocalObj): Boolean = true

    override suspend fun objectToBeUploaded(): List<OperatorLocalObj> = emptyList()

    override suspend fun pushObjectsToServer(objects: List<OperatorLocalObj>) = Unit

    override suspend fun hasSomethingToUpload(): Boolean = false
}

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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import studio.lunabee.core.model.LBResult
import studio.lunabee.synchronization.store.SyncKey
import studio.lunabee.synchronization.store.SyncTimestampLocalDataSource
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
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

    // region single entry point serialization

    @Test
    fun a_manager_synchronized_directly_waits_for_the_in_flight_full_run() = runOperatorTest { store, scope ->
        val order = mutableListOf<String>()
        val fetchGate = CompletableDeferred<Unit>()
        val grouped = FakeOperatorManager(
            store = store,
            scope = scope,
            syncKey = "grouped",
            runOrder = order,
            runId = "grouped",
            fetchGate = fetchGate,
        )
        register("grouped", LBSyncGroup(syncManagers = linkedSetOf(grouped)))
        val direct = FakeOperatorManager(store = store, scope = scope, syncKey = "direct", runOrder = order, runId = "direct")

        val fullRun: Deferred<LBResult<Unit>> = async { LBSyncOperator.syncAllManagers() }
        runCurrent()
        val directRun: Deferred<LBResult<Unit>> = async { LBSyncOperator.sync(manager = direct) }
        runCurrent()

        assertEquals(
            expected = listOf("grouped"),
            actual = order,
            "the direct request has not started while the full run holds the operator",
        )

        fetchGate.complete(Unit)
        assertTrue(fullRun.await() is LBResult.Success, "the full run succeeds")
        assertTrue(directRun.await() is LBResult.Success, "the direct request succeeds")
        assertEquals(expected = listOf("grouped", "direct"), actual = order, "the direct request ran after the full run")
    }

    @Test
    fun a_request_queued_behind_the_operator_pre_empts_the_pending_retry() = runOperatorTest { store, scope ->
        val order = mutableListOf<String>()
        val fetchGate = CompletableDeferred<Unit>()
        val blocking = FakeOperatorManager(
            store = store,
            scope = scope,
            syncKey = "blocking",
            runOrder = order,
            runId = "blocking",
            fetchGate = fetchGate,
        )
        register("blocking", LBSyncGroup(syncManagers = linkedSetOf(blocking)))
        val failing = FakeOperatorManager(
            store = store,
            scope = scope,
            syncKey = "failing",
            runOrder = order,
            runId = "failing",
            failFetchTimes = 1,
            retryTempo = 30.seconds,
        )

        assertTrue(LBSyncOperator.sync(manager = failing) is LBResult.Failure, "the first run fails, scheduling a retry")

        val fullRun: Deferred<LBResult<Unit>> = async { LBSyncOperator.syncAllManagers() }
        runCurrent()
        val queued: Deferred<LBResult<Unit>> = async { LBSyncOperator.sync(manager = failing) }
        runCurrent()
        advanceTimeBy(60.seconds)
        runCurrent()

        assertEquals(
            expected = listOf("failing", "blocking"),
            actual = order,
            "the retry tempo elapsed while the request was queued, but the queued request pre-empted the retry",
        )

        fetchGate.complete(Unit)
        assertTrue(fullRun.await() is LBResult.Success, "the full run succeeds")
        assertTrue(queued.await() is LBResult.Success, "the queued request succeeds")
        assertEquals(
            expected = listOf("failing", "blocking", "failing"),
            actual = order,
            "the queued request ran exactly once, after the full run",
        )
    }

    @Test
    fun sync_group_runs_only_the_named_group() = runOperatorTest { store, scope ->
        val order = mutableListOf<String>()
        register("first", group(store, scope, "a", order = order, id = "first"))
        register("second", group(store, scope, "b", order = order, id = "second"))

        val result = LBSyncOperator.syncGroup(name = "second")

        assertTrue(result is LBResult.Success, "the named group succeeding returns Success")
        assertEquals(expected = listOf("second"), actual = order, "only the named group ran")
    }

    @Test
    fun sync_group_with_an_unknown_key_fails_without_running_anything() = runOperatorTest { store, scope ->
        val order = mutableListOf<String>()
        register("first", group(store, scope, "a", order = order, id = "first"))

        val result = LBSyncOperator.syncGroup(name = "nope")

        assertTrue(result is LBResult.Failure, "an unregistered key returns Failure")
        assertTrue(result.throwable is IllegalArgumentException, "the failure carries an IllegalArgumentException")
        assertTrue(order.isEmpty(), "no group ran")
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
            fresh.setStatusInternal(LBSyncProcessStatus.SyncSuccessfully(Clock.System.now()))
            val freshGroup = LBSyncGroup(
                syncManagers = linkedSetOf(fresh),
                refreshEvents = listOf(LBSyncRefreshEvent.AppForeground(minimumDelay = 1.hours)),
            )

            val stale = FakeOperatorManager(store = store, scope = scope, syncKey = "stale")
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
        store: SyncTimestampLocalDataSource,
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

    private fun runOperatorTest(body: suspend TestScope.(store: SyncTimestampLocalDataSource, scope: CoroutineScope) -> Unit) = runTest {
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
 * Setting [fetchError] makes every run fail (download error), [failFetchTimes] only the first N; setting
 * [fetchGate] parks the fetch — after the [runId] has been recorded — until the gate completes, so a run
 * can be held in flight. Automatic retry is disabled unless [retryTempo] is passed, so a failure resolves
 * immediately under virtual time.
 */
private class FakeOperatorManager(
    store: SyncTimestampLocalDataSource,
    scope: CoroutineScope,
    syncKey: String,
    private val runOrder: MutableList<String>? = null,
    private val runId: String? = null,
    private val fetchError: Exception? = null,
    private val fetchGate: CompletableDeferred<Unit>? = null,
    private var failFetchTimes: Int = 0,
    retryTempo: Duration? = null,
) : LBSyncManager<OperatorServerObj, OperatorLocalObj, Nothing>(scope = scope) {

    init {
        this.retryTempo = retryTempo
    }

    override val syncKey: SyncKey = SyncKey(syncKey)

    override suspend fun clearData() = Unit

    override suspend fun updateData(data: List<OperatorServerObj>) = Unit

    override suspend fun fetchRequest(page: Int, cursor: String?, sinceLastDate: Instant?): FetchPage<OperatorServerObj, Nothing> {
        runId?.let { runOrder?.add(it) }
        fetchGate?.await()
        fetchError?.let { throw it }
        if (failFetchTimes > 0) {
            failFetchTimes -= 1
            throw IllegalStateException("fetch failure #${runId.orEmpty()}")
        }
        return FetchPage(objects = emptyList())
    }

    override fun updatedAt(obj: OperatorServerObj): Instant? = obj.updatedAt

    override fun isInSync(obj: OperatorLocalObj): Boolean = true

    override suspend fun objectToBeUploaded(): List<OperatorLocalObj> = emptyList()

    override suspend fun pushObjectsToServer(objects: List<OperatorLocalObj>) = Unit

    override suspend fun hasSomethingToUpload(): Boolean = false
}

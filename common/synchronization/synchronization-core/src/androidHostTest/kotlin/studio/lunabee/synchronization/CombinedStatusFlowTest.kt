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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import studio.lunabee.synchronization.store.SyncKey
import studio.lunabee.synchronization.store.SyncTimestampLocalDataSource
import studio.lunabee.synchronization.syncmanager.FetchPage
import studio.lunabee.synchronization.syncmanager.LBSyncManager
import studio.lunabee.synchronization.syncmanager.LBSyncProcessStatus
import studio.lunabee.synchronization.testfixture.freshStore
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class CombinedStatusFlowTest {

    @AfterTest
    fun clearOperator() {
        LBSyncOperator.groups.clear()
    }

    // region group: statusByKey reflects member transitions

    @Test
    fun group_statusByKey_reflects_member_transitions() = runFlowTest { store, scope ->
        val m1 = FakeStatusManager(store = store, scope = scope, key = "m1")
        val m2 = FakeStatusManager(store = store, scope = scope, key = "m2")
        val group = LBSyncGroup(syncManagers = linkedSetOf(m1, m2))

        val maps = group.statusByKey().record(scope, testScheduler)
        advanceUntilIdle()

        m1.setStatusInternal(LBSyncProcessStatus.DownloadStarted(now()))
        advanceUntilIdle()
        m2.setStatusInternal(LBSyncProcessStatus.SyncSuccessfully(now()))
        advanceUntilIdle()

        val last = maps().last()
        assertEquals(expected = setOf(SyncKey("m1"), SyncKey("m2")), actual = last.keys, "both managers appear keyed by syncKey")
        assertTrue(last.getValue(SyncKey("m1")) is LBSyncProcessStatus.DownloadStarted, "m1 reflects its latest status")
        assertTrue(last.getValue(SyncKey("m2")) is LBSyncProcessStatus.SyncSuccessfully, "m2 reflects its latest status")
    }

    @Test
    fun group_statusByKey_emits_empty_map_when_no_managers() = runFlowTest { _, scope ->
        val group = LBSyncGroup()

        val maps = group.statusByKey().record(scope, testScheduler)
        advanceUntilIdle()

        assertEquals(expected = listOf(emptyMap()), actual = maps(), "an empty group emits emptyMap once")
    }

    // endregion

    // region group: isSyncing

    @Test
    fun group_isSyncing_true_during_mid_pipeline_false_when_all_terminal() = runFlowTest { store, scope ->
        val m1 = FakeStatusManager(store = store, scope = scope, key = "m1")
        val m2 = FakeStatusManager(store = store, scope = scope, key = "m2")
        val group = LBSyncGroup(syncManagers = linkedSetOf(m1, m2))

        val syncing = group.isSyncing().record(scope, testScheduler)
        advanceUntilIdle()

        m1.setStatusInternal(LBSyncProcessStatus.UploadFinishSuccessfully(processedObjectCount = 1, at = now()))
        advanceUntilIdle()
        assertTrue(syncing().last(), "mid-pipeline UploadFinishSuccessfully counts as syncing")

        m2.setStatusInternal(LBSyncProcessStatus.DownloadStarted(now()))
        advanceUntilIdle()

        m1.setStatusInternal(LBSyncProcessStatus.SyncSuccessfully(now()))
        advanceUntilIdle()
        assertTrue(syncing().last(), "still syncing while m2 is mid-pipeline")
        m2.setStatusInternal(LBSyncProcessStatus.NeverSync)
        advanceUntilIdle()
        assertFalse(syncing().last(), "all members terminal → not syncing")

        // distinctUntilChanged: no consecutive duplicates. Both managers start at NeverSync (false),
        // so the sequence collapses to false, true, false.
        assertEquals(
            expected = listOf(false, true, false),
            actual = syncing(),
            "distinctUntilChanged drops repeated consecutive aggregate values",
        )
    }

    // endregion

    // region group: registry snapshot semantics

    @Test
    fun group_statusByKey_does_not_observe_a_manager_added_after_collection_start() = runFlowTest { store, scope ->
        val m1 = FakeStatusManager(store = store, scope = scope, key = "m1")
        val group = LBSyncGroup(syncManagers = linkedSetOf(m1))

        val maps = group.statusByKey().record(scope, testScheduler)
        advanceUntilIdle()

        val late = FakeStatusManager(store = store, scope = scope, key = "late")
        group.syncManagers.add(late)
        late.setStatusInternal(LBSyncProcessStatus.DownloadStarted(now()))
        m1.setStatusInternal(LBSyncProcessStatus.SyncSuccessfully(now()))
        advanceUntilIdle()

        assertTrue(maps().all { SyncKey("late") !in it.keys }, "the already-running collection never observes the late manager")
        assertEquals(expected = setOf(SyncKey("m1")), actual = maps().last().keys, "only the snapshot member is observed")
    }

    // endregion

    // region operator: spans groups, transitions, snapshot

    @Test
    fun operator_statusByKey_spans_managers_across_multiple_groups() = runFlowTest { store, scope ->
        val a = FakeStatusManager(store = store, scope = scope, key = "a")
        val b = FakeStatusManager(store = store, scope = scope, key = "b")
        LBSyncOperator.groups["g1"] = LBSyncGroup(syncManagers = linkedSetOf(a))
        LBSyncOperator.groups["g2"] = LBSyncGroup(syncManagers = linkedSetOf(b))

        val maps = LBSyncOperator.statusByKey().record(scope, testScheduler)
        advanceUntilIdle()

        a.setStatusInternal(LBSyncProcessStatus.DownloadStarted(now()))
        b.setStatusInternal(LBSyncProcessStatus.SyncSuccessfully(now()))
        advanceUntilIdle()

        val last = maps().last()
        assertEquals(expected = setOf(SyncKey("a"), SyncKey("b")), actual = last.keys, "the operator view spans both groups")
        assertTrue(last.getValue(SyncKey("a")) is LBSyncProcessStatus.DownloadStarted)
        assertTrue(last.getValue(SyncKey("b")) is LBSyncProcessStatus.SyncSuccessfully)
    }

    @Test
    fun operator_isSyncing_true_mid_pipeline_false_when_all_terminal() = runFlowTest { store, scope ->
        val a = FakeStatusManager(store = store, scope = scope, key = "a")
        val b = FakeStatusManager(store = store, scope = scope, key = "b")
        LBSyncOperator.groups["g1"] = LBSyncGroup(syncManagers = linkedSetOf(a))
        LBSyncOperator.groups["g2"] = LBSyncGroup(syncManagers = linkedSetOf(b))

        val syncing = LBSyncOperator.isSyncing().record(scope, testScheduler)
        advanceUntilIdle()

        a.setStatusInternal(LBSyncProcessStatus.DownloadStarted(now()))
        advanceUntilIdle()
        assertTrue(syncing().last(), "any manager mid-pipeline → app-wide syncing")

        a.setStatusInternal(LBSyncProcessStatus.SyncSuccessfully(now()))
        advanceUntilIdle()
        assertFalse(syncing().last(), "every manager terminal → not syncing")

        assertEquals(
            expected = listOf(false, true, false),
            actual = syncing(),
            "distinctUntilChanged drops repeated consecutive aggregate values",
        )
    }

    @Test
    fun operator_statusByKey_does_not_observe_a_group_added_after_collection_start() = runFlowTest { store, scope ->
        val a = FakeStatusManager(store = store, scope = scope, key = "a")
        LBSyncOperator.groups["g1"] = LBSyncGroup(syncManagers = linkedSetOf(a))

        val maps = LBSyncOperator.statusByKey().record(scope, testScheduler)
        advanceUntilIdle()

        val late = FakeStatusManager(store = store, scope = scope, key = "late")
        LBSyncOperator.groups["g2"] = LBSyncGroup(syncManagers = linkedSetOf(late))
        late.setStatusInternal(LBSyncProcessStatus.DownloadStarted(now()))
        a.setStatusInternal(LBSyncProcessStatus.SyncSuccessfully(now()))
        advanceUntilIdle()

        assertTrue(maps().all { SyncKey("late") !in it.keys }, "the already-running collection never observes the late group")
        assertEquals(expected = setOf(SyncKey("a")), actual = maps().last().keys, "only the snapshot member is observed")
    }

    @Test
    fun operator_statusByKey_emits_empty_map_when_no_groups() = runFlowTest { _, scope ->
        val maps = LBSyncOperator.statusByKey().record(scope, testScheduler)
        advanceUntilIdle()

        assertEquals(expected = listOf(emptyMap()), actual = maps(), "no registered manager emits emptyMap once")
    }

    // endregion

    // region test infrastructure

    private fun runFlowTest(body: suspend TestScope.(store: SyncTimestampLocalDataSource, scope: CoroutineScope) -> Unit) = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        try {
            body(freshStore(), scope)
        } finally {
            scope.cancel()
        }
    }

    private companion object {
        private fun now(): Instant = Clock.System.now()
    }
}

/**
 * Eagerly collects [this] flow into a list on an [UnconfinedTestDispatcher] (so the collector resumes
 * synchronously on each emission and observes every transition rather than only the latest conflated
 * value) and returns a getter for the recorded sequence.
 */
private fun <T> Flow<T>.record(scope: CoroutineScope, scheduler: TestCoroutineScheduler): () -> List<T> {
    val recorded = mutableListOf<T>()
    scope.launch(UnconfinedTestDispatcher(scheduler)) {
        collect { recorded += it }
    }
    return { recorded.toList() }
}

private data class CombinedServerObj(val updatedAt: Instant?)

private data class CombinedLocalObj(val id: String)

/**
 * Minimal fake manager whose status is driven only via the engine-internal `setStatusInternal`, so the
 * combined flows can be asserted without running the pipeline. The SPI members are inert.
 */
private class FakeStatusManager(
    store: SyncTimestampLocalDataSource,
    scope: CoroutineScope,
    key: String,
) : LBSyncManager<CombinedServerObj, CombinedLocalObj, Nothing>(providedTimestampStore = store, scope = scope) {

    override val syncKey: SyncKey = SyncKey(key)

    override suspend fun clearData() = Unit

    override suspend fun updateData(data: List<CombinedServerObj>) = Unit

    override suspend fun fetchRequest(page: Int, cursor: String?, sinceLastDate: Instant?): FetchPage<CombinedServerObj, Nothing> =
        FetchPage(objects = emptyList())

    override fun updatedAt(obj: CombinedServerObj): Instant? = obj.updatedAt

    override fun isInSync(obj: CombinedLocalObj): Boolean = true

    override suspend fun objectToBeUploaded(): List<CombinedLocalObj> = emptyList()

    override suspend fun pushObjectsToServer(objects: List<CombinedLocalObj>) = Unit

    override suspend fun hasSomethingToUpload(): Boolean = false
}

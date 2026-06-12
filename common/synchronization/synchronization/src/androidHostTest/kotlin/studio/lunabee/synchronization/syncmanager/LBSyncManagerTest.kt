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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import studio.lunabee.core.model.LBResult
import studio.lunabee.synchronization.store.SyncTimestampStore
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
        // Download (fetch + update) → upload → re-download (fetch + update).
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
            pages = listOf(FetchPage<ServerObj, Nothing>(objects = listOf(ServerObj(maxDate)))),
            supportIncremental = true,
        )

        manager.synchronize()

        assertEquals(
            expected = maxDate.toEpochMilliseconds(),
            actual = store.lastServerSyncDate(syncKey = manager.syncKey),
            "the max ascending updatedAt is persisted as the server cursor",
        )
    }

    @Test
    fun incremental_cursor_is_not_saved_when_incremental_sync_is_unsupported() = runManagerTest { store, scope ->
        val manager = FakeSyncManager(
            store = store,
            scope = scope,
            pages = listOf(FetchPage<ServerObj, Nothing>(objects = listOf(ServerObj(Instant.fromEpochMilliseconds(5_000L))))),
            supportIncremental = false,
        )

        manager.synchronize()

        assertNull(
            store.lastServerSyncDate(syncKey = manager.syncKey),
            "no server cursor is persisted without incremental sync",
        )
    }

    // endregion

    // region paging

    @Test
    fun download_loops_over_every_page_of_a_multi_page_fetch() = runManagerTest { store, scope ->
        val pages = listOf(
            FetchPage<ServerObj, Nothing>(objects = List(size = 2) { ServerObj(Instant.fromEpochMilliseconds(it + 1L)) }),
            FetchPage<ServerObj, Nothing>(objects = List(size = 2) { ServerObj(Instant.fromEpochMilliseconds(it + 10L)) }),
            FetchPage<ServerObj, Nothing>(objects = listOf(ServerObj(Instant.fromEpochMilliseconds(100L)))),
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
    fun cancel_surfaces_download_finish_successfully_terminal_status() = runManagerTest { store, scope ->
        val gate = CompletableDeferred<Unit>()
        val manager = FakeSyncManager(store = store, scope = scope, fetchGate = gate, retryTempo = null)

        val caller: Deferred<LBResult<Unit>> = async { manager.synchronize() }
        advanceUntilIdle() // park inside the first fetch (the gate is never completed)

        // Cancel kills the parked in-flight run; its awaiter resolves as Failure and the terminal status
        // set synchronously by cancelAllRequests() survives (the cancelled pipeline sets nothing further).
        manager.cancelAllRequests()
        advanceUntilIdle()

        assertTrue(
            manager.currentSyncStatus is LBSyncProcessStatus.DownloadFinishSuccessfully,
            "cancel surfaces the legacy DownloadFinishSuccessfully terminal status",
        )
        assertTrue(caller.await() is LBResult.Failure, "a cancelled in-flight synchronize resolves as Failure")
    }

    // endregion

    // region load() seeding

    @Test
    fun load_seeds_status_from_a_pre_populated_store() = runManagerTest { store, scope ->
        store.saveSyncDates(syncKey = FakeSyncManager.SyncKey, serverDateMillis = 1_000L, localDateMillis = 7_000L)
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

        // Several callers arrive while the run is in flight: they collapse into ONE follow-up.
        val collapsed = List(size = 3) { async { manager.synchronize() } }
        advanceUntilIdle()
        assertEquals(expected = 1, actual = manager.fetchCalls, "collapsed callers do not start their own run")

        firstFetch.complete(Unit) // let the in-flight run finish; the single follow-up runs
        advanceUntilIdle()

        assertTrue(first.await() is LBResult.Success)
        collapsed.forEach { assertTrue(it.await() is LBResult.Success, "every collapsed caller receives the follow-up result") }
        assertEquals(expected = 2, actual = manager.fetchCalls, "exactly one follow-up run after the in-flight run")
    }

    // endregion

    // region test infrastructure

    private fun runManagerTest(body: suspend TestScope.(store: SyncTimestampStore, scope: CoroutineScope) -> Unit) = runTest {
        // One scheduler-backed scope shared by the manager AND the DataStore, so the store's I/O is
        // governed by virtual time too: advanceUntilIdle() then deterministically drains cursor reads
        // that happen mid-pipeline (e.g. while another run is parked).
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        try {
            body(freshStore(scope), scope)
        } finally {
            scope.cancel()
        }
    }

    /**
     * Builds a [SyncTimestampStore] over a fresh DataStore living on a unique temp path so no state is
     * shared between tests (copied from SyncTimestampStoreTest), backed by [scope] so its coroutines run
     * on the test scheduler.
     */
    private fun freshStore(scope: CoroutineScope): SyncTimestampStore {
        val fileName = "sync_timestamp_${counter++}_${nextRandom()}.preferences_pb"
        val path: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / fileName
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(scope = scope) { path }
        return SyncTimestampStore(dataStore = dataStore)
    }

    private companion object {
        private var counter: Int = 0

        private fun nextRandom(): Int = (0..Int.MAX_VALUE).random()
    }
}

private data class ServerObj(val updatedAt: Instant?)

private data class LocalObj(val id: String)

/**
 * Fake SPI subclass exercising the observable engine surface only.
 */
private class FakeSyncManager(
    store: SyncTimestampStore,
    scope: CoroutineScope,
    private val pages: List<FetchPage<ServerObj, Nothing>> = listOf(FetchPage<ServerObj, Nothing>(objects = emptyList())),
    private val uploadObjects: List<LocalObj> = emptyList(),
    private val supportChangeNotification: Boolean = false,
    private val supportIncremental: Boolean = false,
    private val pageSize: Int? = null,
    private val fetchError: Exception? = null,
    private val pushError: Exception? = null,
    private val fetchGate: CompletableDeferred<Unit>? = null,
    private val gateOnlyFirstFetch: Boolean = false,
    retryTempo: kotlin.time.Duration? = null,
) : LBSyncManager<ServerObj, LocalObj, Nothing>(timestampStore = store, scope = scope) {

    init {
        this.retryTempo = retryTempo
    }

    var fetchCalls: Int = 0
        private set
    var pushCalls: Int = 0
        private set
    val callLog: MutableList<String> = mutableListOf()
    val updatedPageSizes: MutableList<Int> = mutableListOf()

    override val syncKey: String get() = SyncKey

    override suspend fun clearData() = Unit

    override suspend fun updateData(data: List<ServerObj>) {
        callLog += "update"
        updatedPageSizes += data.size
    }

    override suspend fun fetchRequest(page: Int, cursor: String?, sinceLastDate: Instant?): FetchPage<ServerObj, Nothing> {
        callLog += "fetch"
        val isFirstFetch = fetchCalls == 0
        fetchCalls += 1
        if (fetchGate != null && (!gateOnlyFirstFetch || isFirstFetch)) {
            fetchGate.await()
        }
        fetchError?.let { throw it }
        return pages.getOrElse(page) { FetchPage(objects = emptyList()) }
    }

    override fun updatedAt(obj: ServerObj): Instant? = obj.updatedAt

    override fun isInSync(obj: LocalObj): Boolean = true

    override suspend fun objectToBeUploaded(): List<LocalObj> = uploadObjects

    override suspend fun pushObjectsToServer(objects: List<LocalObj>) {
        callLog += "push"
        pushCalls += 1
        pushError?.let { throw it }
    }

    override suspend fun hasSomethingToUpload(): Boolean = uploadObjects.isNotEmpty()

    override fun queryPageSize(): Int? = pageSize

    override fun supportIncrementalSync(): Boolean = supportIncremental

    override fun supportChangeNotificationFromServer(): Boolean = supportChangeNotification

    /**
     * Eagerly collects [status] into a list of simple class names on an [UnconfinedTestDispatcher] (so
     * the collector resumes synchronously on each emission and observes every transition the pipeline
     * yields between, rather than only the latest conflated value) and returns a getter for the recorded
     * sequence.
     */
    fun recordStatuses(scope: CoroutineScope, scheduler: TestCoroutineScheduler): () -> List<String> {
        val recorded = mutableListOf<String>()
        scope.launch(UnconfinedTestDispatcher(scheduler)) {
            status.collect { recorded += it::class.simpleName.orEmpty() }
        }
        return { recorded.toList() }
    }

    companion object {
        const val SyncKey: String = "FakeSyncManager"
    }
}

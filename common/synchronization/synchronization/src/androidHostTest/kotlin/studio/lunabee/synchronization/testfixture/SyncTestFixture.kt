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

package studio.lunabee.synchronization.testfixture

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import studio.lunabee.synchronization.store.SyncTimestampStore
import studio.lunabee.synchronization.syncmanager.FetchPage
import studio.lunabee.synchronization.syncmanager.LBSyncManager
import kotlin.time.Duration
import kotlin.time.Instant

/*
 * Shared test fixture for the `:synchronization` engine JVM host tests.
 *
 * Centralises the fakes and the scheduler-backed harness so every test file reuses one set of
 * `internal` symbols instead of redeclaring duplicate private ones. New test files import these.
 */

/**
 * Minimal server object the fake managers download.
 *
 * @property id stable identity used by the conflict/state tests; defaults to empty for the count-only
 * pipeline tests that ignore identity.
 * @property updatedAt the ascending incremental-sync cursor source.
 */
internal data class ServerObj(val id: String = "", val updatedAt: Instant?)

/**
 * Minimal local object the fake managers upload.
 *
 * @property id stable identity used by the conflict/state tests.
 */
internal data class LocalObj(val id: String)

/**
 * The captured arguments of a single [LBSyncManager.fetchRequest] call, so paging/cursor/seeding
 * threading can be asserted on observable inputs.
 */
internal data class FetchArgs(val page: Int, val cursor: String?, val sinceLastDate: Instant?)

/**
 * Fake SPI subclass exercising the observable engine surface only.
 *
 * Superset of every per-test knob the engine tests need:
 * - download shape: [pages], [pageSize], [hasNextPageOverride] ([pageInfo]-driven), [fetchError];
 * - upload shape: [uploadObjects], [pushError], [pushErrorOnAttempt] (throw only on the Nth push);
 * - incremental/notification gates: [supportIncremental], [supportChangeNotification];
 * - concurrency probes: [fetchGate], [gateOnlyFirstFetch];
 * - retry: [retryTempo].
 *
 * Captured for assertions: [fetchArgs], [pushedBatches], [fetchCalls], [pushCalls], [callLog],
 * [updatedPageSizes].
 */
@Suppress("LongParameterList")
internal open class FakeSyncManager(
    store: SyncTimestampStore,
    scope: CoroutineScope,
    private val pages: List<FetchPage<ServerObj, Int>> = listOf(FetchPage<ServerObj, Int>(objects = emptyList())),
    private val uploadObjects: List<LocalObj> = emptyList(),
    private val supportChangeNotification: Boolean = false,
    private val supportIncremental: Boolean = false,
    private val pageSize: Int? = null,
    private val fetchError: Exception? = null,
    private val pushError: Exception? = null,
    private val pushErrorOnAttempt: Int? = null,
    private val fetchGate: CompletableDeferred<Unit>? = null,
    private val gateOnlyFirstFetch: Boolean = false,
    private val hasNextPageOverride: ((pageInfo: Int) -> Boolean)? = null,
    retryTempo: Duration? = null,
) : LBSyncManager<ServerObj, LocalObj, Int>(timestampStore = store, scope = scope) {

    init {
        this.retryTempo = retryTempo
    }

    var fetchCalls: Int = 0
        private set
    var pushCalls: Int = 0
        private set
    val callLog: MutableList<String> = mutableListOf()
    val updatedPageSizes: MutableList<Int> = mutableListOf()
    val fetchArgs: MutableList<FetchArgs> = mutableListOf()
    val pushedBatches: MutableList<List<LocalObj>> = mutableListOf()

    override val syncKey: String get() = SyncKey

    override suspend fun clearData() = Unit

    override suspend fun updateData(data: List<ServerObj>) {
        callLog += "update"
        updatedPageSizes += data.size
    }

    override suspend fun fetchRequest(page: Int, cursor: String?, sinceLastDate: Instant?): FetchPage<ServerObj, Int> {
        callLog += "fetch"
        fetchArgs += FetchArgs(page = page, cursor = cursor, sinceLastDate = sinceLastDate)
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
        pushedBatches += objects
        pushErrorOnAttempt?.let { if (pushCalls == it) throw PushAttemptException(attempt = it) }
        pushError?.let { throw it }
    }

    override suspend fun hasSomethingToUpload(): Boolean = uploadObjects.isNotEmpty()

    override fun queryPageSize(): Int? = pageSize

    override fun supportIncrementalSync(): Boolean = supportIncremental

    override fun supportChangeNotificationFromServer(): Boolean = supportChangeNotification

    override fun hasNextPage(pageInfo: Int): Boolean =
        hasNextPageOverride?.invoke(pageInfo) ?: super.hasNextPage(pageInfo)

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

/** Thrown by [FakeSyncManager] when [FakeSyncManager.pushErrorOnAttempt] fires on a given push attempt. */
internal class PushAttemptException(attempt: Int) : Exception("push failed on attempt $attempt")

/** Thrown by [StatefulFakeSyncManager.failNextPush] to model a one-shot transient push failure. */
internal class TransientPushException : Exception("transient push failure")

/**
 * Stateful fake backed by an in-memory dao, used by the conflict tests to model the engine's
 * **last-write-wins by `updatedAt`** behaviour (no field-level merge).
 *
 * - [updateData] UPSERTS each downloaded server object into [dao], marking it in-sync.
 * - [objectToBeUploaded] returns the entries not currently in-sync (the pending local writes).
 * - [pushObjectsToServer] marks the pushed entries in-sync.
 * - [seedLocalDirty] seeds a locally-edited (not-in-sync) record before a sync.
 *
 * @param downloadPages the server pages returned by successive fetches (a download upserts them).
 */
internal class StatefulFakeSyncManager(
    store: SyncTimestampStore,
    scope: CoroutineScope,
    private val downloadPages: List<FetchPage<ServerObj, Int>> = listOf(FetchPage<ServerObj, Int>(objects = emptyList())),
    retryTempo: Duration? = null,
) : LBSyncManager<ServerObj, LocalObj, Int>(timestampStore = store, scope = scope) {

    init {
        this.retryTempo = retryTempo
    }

    /** id → stored object. */
    val dao: MutableMap<String, ServerObj> = mutableMapOf()

    /** id → in-sync flag (false means a pending local write awaiting upload). */
    val inSyncById: MutableMap<String, Boolean> = mutableMapOf()

    val callLog: MutableList<String> = mutableListOf()
    val pushedBatches: MutableList<List<LocalObj>> = mutableListOf()

    var fetchCalls: Int = 0
        private set
    var pushCalls: Int = 0
        private set

    /** When true, the next [pushObjectsToServer] throws once (then clears) to model a transient failure. */
    private var failPushOnce: Boolean = false

    override val syncKey: String get() = SyncKey

    /** Seed a locally-edited record marked not-in-sync (a pending upload) before running a sync. */
    fun seedLocalDirty(obj: ServerObj) {
        dao[obj.id] = obj
        inSyncById[obj.id] = false
    }

    /** Arm a one-shot transient push failure: the next push throws, leaving the records pending. */
    fun failNextPush() {
        failPushOnce = true
    }

    override suspend fun clearData() {
        dao.clear()
        inSyncById.clear()
    }

    override suspend fun updateData(data: List<ServerObj>) {
        callLog += "update"
        data.forEach { obj ->
            // Last-write-wins upsert: the downloaded server object overwrites the local one.
            dao[obj.id] = obj
            inSyncById[obj.id] = true
        }
    }

    override suspend fun fetchRequest(page: Int, cursor: String?, sinceLastDate: Instant?): FetchPage<ServerObj, Int> {
        callLog += "fetch"
        fetchCalls += 1
        return downloadPages.getOrElse(page) { FetchPage(objects = emptyList()) }
    }

    override fun updatedAt(obj: ServerObj): Instant? = obj.updatedAt

    override fun isInSync(obj: LocalObj): Boolean = inSyncById[obj.id] != false

    override suspend fun objectToBeUploaded(): List<LocalObj> =
        dao.keys.filter { inSyncById[it] == false }.map { LocalObj(it) }

    override suspend fun pushObjectsToServer(objects: List<LocalObj>) {
        callLog += "push"
        pushCalls += 1
        pushedBatches += objects
        if (failPushOnce) {
            // Leave the records pending so the retry still has them to push.
            failPushOnce = false
            throw TransientPushException()
        }
        objects.forEach { inSyncById[it.id] = true }
    }

    override suspend fun hasSomethingToUpload(): Boolean = inSyncById.values.any { !it }

    companion object {
        const val SyncKey: String = "StatefulFakeSyncManager"
    }
}

/**
 * Runs [body] inside [runTest] with a single scheduler-backed scope shared by the manager AND the
 * DataStore, so the store's I/O is governed by virtual time too: `advanceUntilIdle()` then
 * deterministically drains cursor reads that happen mid-pipeline (e.g. while another run is parked).
 */
internal fun runManagerTest(
    body: suspend TestScope.(store: SyncTimestampStore, scope: CoroutineScope) -> Unit,
) = runTest {
    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
    try {
        body(freshStore(scope), scope)
    } finally {
        scope.cancel()
    }
}

/**
 * Builds a [SyncTimestampStore] over a fresh DataStore living on a unique temp path so no state is
 * shared between tests, backed by [scope] so its coroutines run on the test scheduler.
 */
internal fun freshStore(scope: CoroutineScope): SyncTimestampStore {
    val fileName = "sync_fixture_${storeCounter++}_${nextRandom()}.preferences_pb"
    val path: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / fileName
    val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(scope = scope) { path }
    return SyncTimestampStore(dataStore = dataStore)
}

private var storeCounter: Int = 0

private fun nextRandom(): Int = (0..Int.MAX_VALUE).random()

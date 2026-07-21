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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import studio.lunabee.synchronization.store.LBSyncStorage
import studio.lunabee.synchronization.store.SyncKey
import studio.lunabee.synchronization.store.SyncTimestampLocalDataSource
import studio.lunabee.synchronization.syncmanager.FetchPage
import studio.lunabee.synchronization.syncmanager.LBSyncManager
import kotlin.time.Duration
import kotlin.time.Instant

internal data class ServerObj(val id: String = "", val updatedAt: Instant?)

internal data class LocalObj(val id: String)

internal data class FetchArgs(val page: Int, val cursor: String?, val sinceLastDate: Instant?)

@Suppress("LongParameterList")
internal open class FakeSyncManager(
    store: SyncTimestampLocalDataSource,
    scope: CoroutineScope,
    private val pages: List<FetchPage<ServerObj, Int>> = listOf(FetchPage<ServerObj, Int>(objects = emptyList())),
    private val uploadObjects: List<LocalObj> = emptyList(),
    private val supportChangeNotification: Boolean = false,
    private val supportIncremental: Boolean = false,
    private val pageSize: Int? = null,
    private val fetchError: Exception? = null,
    private val fetchErrorOnPage: Int? = null,
    private val pushError: Exception? = null,
    private val pushErrorOnAttempt: Int? = null,
    private val objectToBeUploadedError: Exception? = null,
    private val fetchGate: CompletableDeferred<Unit>? = null,
    private val gateOnlyFirstFetch: Boolean = false,
    private val hasNextPageOverride: ((pageInfo: Int) -> Boolean)? = null,
    retryTempo: Duration? = null,
) : LBSyncManager<ServerObj, LocalObj, Int>(scope = scope) {

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

    override val syncKey: SyncKey get() = SyncKey(SyncKeyValue)

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
        fetchErrorOnPage?.let { if (page == it) throw FetchPageException(page = it) }
        return pages.getOrElse(page) { FetchPage(objects = emptyList()) }
    }

    override fun updatedAt(obj: ServerObj): Instant? = obj.updatedAt

    override fun isInSync(obj: LocalObj): Boolean = true

    override suspend fun objectToBeUploaded(): List<LocalObj> {
        objectToBeUploadedError?.let { throw it }
        return uploadObjects
    }

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
        const val SyncKeyValue: String = "FakeSyncManager"
    }
}

internal class PushAttemptException(attempt: Int) : Exception("push failed on attempt $attempt")

internal class FetchPageException(page: Int) : Exception("fetch failed on page $page")

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
    store: SyncTimestampLocalDataSource,
    scope: CoroutineScope,
    private val downloadPages: List<FetchPage<ServerObj, Int>> = listOf(FetchPage<ServerObj, Int>(objects = emptyList())),
    retryTempo: Duration? = null,
) : LBSyncManager<ServerObj, LocalObj, Int>(scope = scope) {

    init {
        this.retryTempo = retryTempo
    }

    val dao: MutableMap<String, ServerObj> = mutableMapOf()

    val inSyncById: MutableMap<String, Boolean> = mutableMapOf()

    val callLog: MutableList<String> = mutableListOf()
    val pushedBatches: MutableList<List<LocalObj>> = mutableListOf()

    var fetchCalls: Int = 0
        private set
    var pushCalls: Int = 0
        private set

    private var failPushOnce: Boolean = false

    override val syncKey: SyncKey get() = SyncKey(SyncKeyValue)

    fun seedLocalDirty(obj: ServerObj) {
        dao[obj.id] = obj
        inSyncById[obj.id] = false
    }

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
            failPushOnce = false
            throw TransientPushException()
        }
        objects.forEach { inSyncById[it.id] = true }
    }

    override suspend fun hasSomethingToUpload(): Boolean = inSyncById.values.any { !it }

    companion object {
        const val SyncKeyValue: String = "StatefulFakeSyncManager"
    }
}

internal fun runManagerTest(
    body: suspend TestScope.(store: SyncTimestampLocalDataSource, scope: CoroutineScope) -> Unit,
) = runTest {
    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
    try {
        body(freshStore(), scope)
    } finally {
        scope.cancel()
    }
}

/**
 * Creates a fresh in-memory store and installs it as the process-wide [LBSyncStorage] backend, since
 * managers resolve their store exclusively through [LBSyncStorage]. Installing per test keeps tests
 * isolated (last install wins).
 */
internal fun freshStore(): SyncTimestampLocalDataSource =
    FakeSyncTimestampLocalDataSource().also(LBSyncStorage::install)

/**
 * In-memory [SyncTimestampLocalDataSource] fake keyed by `syncKey`, mirroring the contract's non-null write
 * semantics (a `null` argument leaves the corresponding stored value unchanged).
 */
internal class FakeSyncTimestampLocalDataSource : SyncTimestampLocalDataSource {

    private val serverDates: MutableMap<SyncKey, Instant> = mutableMapOf()
    private val localDates: MutableMap<SyncKey, Instant> = mutableMapOf()

    override suspend fun lastServerSyncDate(syncKey: SyncKey): Instant? = serverDates[syncKey]

    override suspend fun lastSuccessfulSyncDate(syncKey: SyncKey): Instant? = localDates[syncKey]

    override suspend fun saveSyncDates(syncKey: SyncKey, serverDate: Instant?, localDate: Instant?) {
        serverDate?.let { serverDates[syncKey] = it }
        localDate?.let { localDates[syncKey] = it }
    }

    override suspend fun clear(syncKey: SyncKey) {
        serverDates.remove(syncKey)
        localDates.remove(syncKey)
    }

    override suspend fun clearAll() {
        serverDates.clear()
        localDates.clear()
    }
}

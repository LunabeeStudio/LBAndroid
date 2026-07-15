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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import studio.lunabee.core.model.LBResult
import studio.lunabee.logger.LBLogger
import studio.lunabee.synchronization.runner.SyncRunner
import studio.lunabee.synchronization.store.LBSyncStorage
import studio.lunabee.synchronization.store.SyncKey
import studio.lunabee.synchronization.store.SyncTimestampStore
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Generic [LBSyncManager]
 */
typealias LBGenericSyncManager = LBSyncManager<*, *, *>

/**
 * Default [LBSyncManager]
 */
typealias LBDefaultSyncManager<ServerData, LocalData> = LBSyncManager<ServerData, LocalData, Nothing>

/**
 * LBSyncManager abstract class.
 * Subclass it to implement every SyncManager you need.
 *
 * Coroutine-native engine: [synchronize] is a suspend function returning [LBResult], status is exposed
 * as a [StateFlow], and run scheduling (collapse-and-join + automatic retry) is delegated to
 * [SyncRunner]. The detached-from-caller execution of the old `GlobalScope.launch` is preserved by the
 * injected [scope].
 *
 * @param ServerData The type of data returned by the server
 * @param LocalData The type of data to be mapped from [ServerData]
 * @param PageInfo The type of data returned by the server to handle pagination. Could be [Nothing] if non applicable.
 * @param timestampStore storage-agnostic persistence for this manager's sync cursors, shared
 * process-wide with every other manager and the operator.
 * @param scope the scope every sync run and automatic retry is launched in. The no-store secondary
 * constructor passes the shared library [defaultSyncScope].
 * @param logging Enable LBSM logs
 */
@Suppress("unused", "TooManyFunctions")
abstract class LBSyncManager<ServerData, LocalData, PageInfo> internal constructor(
    providedTimestampStore: SyncTimestampStore?,
    internal val scope: CoroutineScope,
    private var logging: Boolean = true,
) {

    /**
     * Convenience constructor preserving the legacy call shape: the cursor store is resolved from the
     * process-wide [LBSyncStorage] backend (install it once at startup) and runs are launched in the
     * shared library [defaultSyncScope]. The store is read lazily, so [LBSyncStorage.install] only has to
     * run before the first synchronization, not before this manager is created.
     *
     * @param logging Enable LBSM logs.
     */
    constructor(logging: Boolean = true) : this(providedTimestampStore = null, scope = defaultSyncScope, logging = logging)

    /**
     * Resolved lazily so a manager built with the no-store constructor decouples its creation from
     * [LBSyncStorage.install] ordering: the installed backend is read on first cursor access. Tests and
     * advanced DI inject an explicit store through the `internal` primary constructor.
     */
    private val timestampStore: SyncTimestampStore by lazy { providedTimestampStore ?: LBSyncStorage.requireStore() }

    /**
     * Persistence key namespace for this manager's sync cursors. Defaults to the class simple name to
     * preserve the legacy SharedPreferences key scheme (`"${simpleName}lastSyncDate"`); override it to
     * make a subclass rename-safe so the incremental-sync cursor survives a class rename.
     */
    open val syncKey: SyncKey get() = SyncKey(this::class.simpleName.orEmpty())

    /**
     * Used to log : You can manage log in constructor with the field logging
     */
    protected var logger: Logger? = if (logging) LBLogger.get("LBSM ${this::class.simpleName} ${this.hashCode()}") else null

    /**
     * Drives this manager's single in-flight run: concurrent [synchronize] calls collapse into one
     * follow-up whose real result every caller receives, and a failed run is retried after [retryTempo].
     */
    private val syncRunner: SyncRunner = SyncRunner(scope = scope, retryDelay = { retryTempo })

    /*===============
     * Public Fields
     ===============*/

    /**
     * The delay before an automatic retry of a failed sync, re-read each time a retry is scheduled.
     * Set to `null` to disable automatic retry. Defaults to 30 seconds.
     */
    open var retryTempo: Duration? = 30.seconds

    private val _status: MutableStateFlow<LBSyncProcessStatus> = MutableStateFlow(LBSyncProcessStatus.NeverSync)

    /**
     * The sync manager's status as a state flow. Collect it to observe transitions; conflation applies
     * (status is state, not an event stream).
     */
    val status: StateFlow<LBSyncProcessStatus> = _status.asStateFlow()

    /**
     * Read-only alias for the current value of [status]. **MUST NOT** be edited outside a
     * [LBSyncManager] (sub)class; the engine mutates it internally.
     */
    val currentSyncStatus: LBSyncProcessStatus get() = _status.value

    /**
     * Engine-internal status mutator: publishes [status] on the state flow and logs its description.
     * Visible to [studio.lunabee.synchronization.LBSyncGroup] and
     * [studio.lunabee.synchronization.LBSyncOperator] so they can set `Disabled`/`PendingSync` while
     * still routing every mutation through one place.
     */
    internal fun setStatusInternal(status: LBSyncProcessStatus) {
        _status.value = status
        logger?.v(status.fullDescription())
    }

    /**
     * Seed [status] from the persisted last successful sync date. Status stays
     * [LBSyncProcessStatus.NeverSync] until this is called (the cursor read is suspending, so it can no
     * longer run synchronously in `init`).
     */
    suspend fun load() {
        setStatusInternal(
            timestampStore.lastSuccessfulSyncDate(syncKey)?.let {
                LBSyncProcessStatus.SyncSuccessfully(it)
            } ?: LBSyncProcessStatus.NeverSync,
        )
    }

    /*=============================
     * Abstract methods: local side
     =============================*/

    /**
     * Clear the data managed by the sync manager
     * Called when reset the sync manager
     * Can be used at logout for example
     */
    protected abstract suspend fun clearData()

    /**
     * Save the new fresh data you just got from the sync download
     * @param data: object list to be updated
     */
    protected abstract suspend fun updateData(data: List<ServerData>)
    /*===============================
     * Abstract methods: server side
     ===============================*/

    /**
     * How to fetch one page of data from the server. Throw on error: the engine catches at the pipeline
     * boundary and maps the failure to [LBSyncProcessStatus.DownloadFinishWithError].
     *
     * **WARNING** : objects returned must be ordered by ascending `updatedAt` when incremental sync is
     * enabled.
     *
     * @param page: current page value, starting at 0.
     * @param cursor: opaque cursor returned by the previous page's [FetchPage.nextCursor], or `null` for
     * the first page.
     * @param sinceLastDate: the last server `updatedAt` cursor, or `null` to fetch from the beginning.
     * @return the fetched page.
     */
    protected abstract suspend fun fetchRequest(
        page: Int = 0,
        cursor: String? = null,
        sinceLastDate: Instant?,
    ): FetchPage<ServerData, PageInfo>

    /**
     * How to get the updatedAt information from the record coming from the server
     * @param obj: server object
     * @return the nullable updatedAt instant
     */
    protected abstract fun updatedAt(obj: ServerData): Instant?

    /**
     * How to know whether an object is in sync with the server
     * @param obj: the app object
     * @return true if the app object is in sync with the server
     */
    protected abstract fun isInSync(obj: LocalData): Boolean

    /**
     * @return the list of objects you need to upload to the server
     */
    protected abstract suspend fun objectToBeUploaded(): List<LocalData>

    /**
     * How to upload objects to the server. Throw on failure: the engine catches at the pipeline boundary
     * and maps the failure to [LBSyncProcessStatus.UploadFinishWithError].
     *
     * **WARNING** : Must run on a background thread to avoid blocking the UI.
     *
     * @param objects: the object list to push.
     */
    protected abstract suspend fun pushObjectsToServer(objects: List<LocalData>)

    /**
     * @return true if some objects need to be uploaded to the server
     */
    abstract suspend fun hasSomethingToUpload(): Boolean
    /*======================
     * Overrideable methods
     ======================*/

    /**
     * Override this if you want to support paging
     * @return the number of object you want to fetch by page
     */
    protected open fun queryPageSize(): Int? = null

    /**
     * You can activate this option to optimize a sync failure.
     * This requires records fetched to be ordered by ascending updatedAt
     */
    protected open fun supportIncrementalSync(): Boolean = false

    /**
     * Return true if the client is notified by the server when a record is uploaded on it
     * eg: Parse LiveQuery
     */
    open fun supportChangeNotificationFromServer(): Boolean = false

    /**
     * Start the listener for the server notifications.
     * eg: Start the Parse LiveQuery
     *
     * @return true once the listener is started.
     */
    open suspend fun startServerNotificationListener(): Boolean = true

    /**
     * Stop the listener for the server notifications.
     * eg: stop the Parse LiveQuery
     *
     * @return true once the listener is stopped.
     */
    open suspend fun stopServerNotificationListener(): Boolean = true
    /*================
     * Public methods
     ================*/

    /**
     * Cancel the in-flight sync run and any pending automatic retry. The terminal status surfaced is
     * [LBSyncProcessStatus.DownloadFinishSuccessfully] (parity with the legacy soft-cancel path).
     *
     * An in-flight [synchronize] call returns an [LBResult.Failure] carrying the cancellation cause
     * (this is how [SyncRunner] resolves cancelled awaiters so they never hang); the important parity is
     * the terminal **status**, not the returned result. A [CancellationException] never escapes the
     * engine.
     */
    fun cancelAllRequests() {
        setStatusInternal(LBSyncProcessStatus.DownloadFinishSuccessfully(Clock.System.now()))
        syncRunner.cancel()
    }

    /**
     * Synchronize the sync manager: download data, then upload data if needed, then (unless the server
     * notifies of changes) re-download. Concurrent calls collapse into a single follow-up run via
     * [SyncRunner]; a failed run is retried automatically after [retryTempo].
     *
     * @return [LBResult.Success] when the pipeline completed, or [LBResult.Failure] carrying the cause.
     */
    suspend fun synchronize(): LBResult<Unit> = syncRunner.run { runPipeline() }

    /**
     * Reset the sync manager
     * Clear the data, the sync status and dates timestamp
     */
    suspend fun resetData() {
        resetTimeStamp()
        clearData()
        setStatusInternal(LBSyncProcessStatus.NeverSync)
    }

    fun resetSyncStatus() {
        setStatusInternal(LBSyncProcessStatus.NeverSync)
    }

    suspend fun resetTimeStamp() {
        cancelAllRequests()
        timestampStore.clear(syncKey)
        logger?.v("Reset last updated date")
    }

    /**
     * Get the last successful sync date (device version) from the timestamp store.
     * @return the nullable last device sync instant.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    suspend fun lastSuccessfulSyncDate(): Instant? =
        timestampStore.lastSuccessfulSyncDate(syncKey)

    /*=================
     * Private methods
     =================*/

    /**
     * Runs the full sync pipeline once, linearizing the legacy download → upload → conditional
     * re-download flow. SPI throws are caught at this boundary, mapped to the matching `*WithError`
     * status, and returned as [LBResult.Failure] so [SyncRunner] schedules a retry.
     *
     * @return [LBResult.Success] when the pipeline completed, or [LBResult.Failure] carrying the SPI
     * error.
     */
    private suspend fun runPipeline(): LBResult<Unit> {
        return try {
            download()
            val uploaded = upload()
            if (uploaded && !supportChangeNotificationFromServer()) {
                download()
            }
            setStatusInternal(LBSyncProcessStatus.SyncSuccessfully(Clock.System.now()))
            LBResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LBResult.Failure(e)
        }
    }

    /**
     * Uploads the pending objects, if any. On a non-empty push the status transitions
     * `UploadStarted` → `UploadFinishSuccessfully`; a push failure maps to `UploadFinishWithError` and
     * rethrows.
     *
     * @return true if a push was attempted (so a re-download may follow), false if nothing was pending.
     */
    private suspend fun upload(): Boolean {
        val objects = objectToBeUploaded()
        if (objects.isEmpty()) return false

        setStatusInternal(LBSyncProcessStatus.UploadStarted(Clock.System.now()))
        try {
            pushObjectsToServer(objects)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setStatusInternal(LBSyncProcessStatus.UploadFinishWithError(error = e, at = Clock.System.now()))
            throw e
        }
        setStatusInternal(LBSyncProcessStatus.UploadFinishSuccessfully(processedObjectCount = objects.size, at = Clock.System.now()))
        return true
    }

    /**
     * Downloads every page from the server. Status transitions `DownloadStarted` →
     * `DownloadUpdated` (per page) → `DownloadFinishSuccessfully`; a fetch failure maps to
     * `DownloadFinishWithError` and rethrows.
     *
     * Cursor persistence mirrors the legacy engine: the server cursor is checkpointed **per page**
     * only when [supportIncrementalSync] (so a mid-paging failure can resume from the last saved
     * page), and is **always** persisted together with the local sync date on terminal success —
     * even for a non-incremental manager, whose next run then fetches only records newer than that
     * cursor. [supportIncrementalSync] therefore governs mid-paging resumption, not whether the
     * cursor filter applies on the following run.
     */
    private suspend fun download() {
        setStatusInternal(LBSyncProcessStatus.DownloadStarted(Clock.System.now()))

        val lastUpdatedDate: Instant? = lastServerUpdatedDate()
        var page = 0
        var cursor: String? = null
        var maxDate: Instant? = lastUpdatedDate

        try {
            while (true) {
                val fetchPage = fetchRequest(page = page, cursor = cursor, sinceLastDate = lastUpdatedDate)
                val objects = fetchPage.objects

                maxDate = listOfNotNull(
                    objects.mapNotNull(::updatedAt).maxOrNull(),
                    maxDate,
                ).maxOrNull()

                updateData(objects)
                if (supportIncrementalSync()) {
                    saveDownloadDate(maxDate)
                }
                setStatusInternal(LBSyncProcessStatus.DownloadUpdated(processedObjectCount = objects.size, at = Clock.System.now()))

                if (hasNextPage(objects.size, fetchPage.pageInfo)) {
                    page += 1
                    cursor = fetchPage.nextCursor
                } else {
                    break
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setStatusInternal(LBSyncProcessStatus.DownloadFinishWithError(error = e, at = Clock.System.now()))
            throw e
        }

        setStatusInternal(LBSyncProcessStatus.DownloadFinishSuccessfully(Clock.System.now()))
        // Terminal success always records both the local sync date and the server cursor (legacy
        // parity); the per-page checkpoint above is the only part gated on incremental sync.
        saveDownloadDate(maxDate)
    }

    /**
     * Save the last download date in the timestamp store: always the device date, and the server
     * [instant] when non-null (the [SyncTimestampStore] leaves a `null` server value unchanged).
     * @param instant A server instant to save, or null to leave the server cursor untouched.
     */
    private suspend fun saveDownloadDate(instant: Instant?) {
        timestampStore.saveSyncDates(
            syncKey = syncKey,
            serverDate = instant,
            localDate = Clock.System.now(),
        )
    }

    /**
     * Get the last download date (server version) from the timestamp store.
     * @return the nullable last server download instant.
     */
    private suspend fun lastServerUpdatedDate(): Instant? =
        timestampStore.lastServerSyncDate(syncKey)

    /**
     * Called to know if the sync manager should query the next page using a custom data.
     * @param pageInfo Data used to determine the pagination state. By default, the number of objects returned by the query as integer.
     * @return true if paged query has a next page
     */
    protected open fun hasNextPage(pageInfo: PageInfo): Boolean = false

    private fun hasNextPage(objectCount: Int, pageInfo: PageInfo?): Boolean {
        return if (pageInfo != null) {
            hasNextPage(pageInfo)
        } else {
            // Fallback to objects size if pageInfo has not been specified, default behavior
            queryPageSize() == objectCount
        }
    }
}

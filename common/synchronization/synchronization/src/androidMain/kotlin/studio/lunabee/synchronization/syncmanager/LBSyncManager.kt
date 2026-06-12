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

import android.content.Context
import bolts.Task
import bolts.TaskCompletionSource
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import studio.lunabee.core.model.LBResult
import studio.lunabee.logger.LBLogger
import studio.lunabee.synchronization.store.SyncTimestampStore
import studio.lunabee.synchronization.store.syncTimestampStore
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
 * @param ServerData The type of data returned by the server
 * @param LocalData The type of data to be mapped from [ServerData]
 * @param PageInfo The type of data returned by the server to handle pagination. Could be [Nothing] if non applicable.
 * @param context Context used to access shared preferences
 * @param logging Enable LBSM logs
 */
@Suppress("unused")
abstract class LBSyncManager<ServerData, LocalData, PageInfo>(
    context: Context,
    private var logging: Boolean = true,
) {

    /**
     * Persistence key namespace for this manager's sync cursors. Defaults to the class simple name to
     * preserve the legacy SharedPreferences key scheme (`"${simpleName}lastSyncDate"`); override it to
     * make a subclass rename-safe so the incremental-sync cursor survives a class rename.
     */
    open val syncKey: String get() = this::class.simpleName.orEmpty()

    /**
     * DataStore-backed persistence for this manager's sync cursors, shared process-wide with every
     * other manager and the operator.
     */
    private val timestampStore: SyncTimestampStore = context.applicationContext.syncTimestampStore

    /**
     * This is used to "post" cancel a request.
     * Case of use : You trigger a sync, you logout, you logging and
     * you don't want to get the result from the previous account
     */
    private var requestId: String? = null

    /**
     * The timer in case of retry
     */
    private var retryTimer: Timer? = null

    /**
     * The task to execute in case of retry
     */
    private var retryTimerTask: TimerTask? = null

    /**
     * HashSet of observers -> notify when sync status change
     */
    private var syncObservers: ConcurrentLinkedQueue<LBSyncToken> = ConcurrentLinkedQueue()

    /**
     * Used to log : You can manage log in constructor with the field logging
     */
    protected var logger: Logger? = if (logging) LBLogger.get("LBSM ${this::class.simpleName} ${this.hashCode()}") else null

    /**
     * Is the sync manager is in a dirty state
     * Setting the value automatically cancel any current retry timer
     * If the new value is true, a new retry timer is created
     */
    private var syncIsDirty: Boolean = false
        set(value) {
            field = value
            retryTimer?.cancel()
            retryTimer = null
            retryTimerTask?.cancel()
            retryTimerTask = null
            if (value) {
                retryTimerTask = retryTimerTask()
                retryTempoInMs?.let {
                    retryTimer = Timer()
                    try {
                        retryTimer?.schedule(retryTimerTask, it)
                    } catch (e: Exception) {
                        // retryTimerTask as been set to null as syncIsDirty has be reassigned before the schedule is launched
                        // exception can be NullPointerException if retryTimerTask == null
                        // or IllegalStateException if retryTimerTask has been canceled
                    }
                }
            }
        }
    /*===============
     * Public Fields
     ===============*/

    /**
     * Can be modified to change retry tempo, value is in milliseconds
     */
    open var retryTempoInMs: Long? = 30 * 1000

    /**The sync manager's current status : please see LBSyncProcessStatus
     * Every time this value is changed, all the LBSyncTokens are notify
     * **MUST NOT** be edited outside a LBSyncManager (sub)class
     */
    var currentSyncStatus: LBSyncProcessStatus = LBSyncProcessStatus.NeverSync
        internal set(value) {
            field = value
            syncObservers.forEach { token ->
                token.changeStatusClosure(value)
            }
            logger?.v(value.fullDescription())
        }

    /**
     * Seed [currentSyncStatus] from the persisted last successful sync date. Status stays
     * [LBSyncProcessStatus.NeverSync] until this is called (the cursor read is suspending, so it can no
     * longer run synchronously in `init`).
     */
    suspend fun load() {
        currentSyncStatus = timestampStore.lastSuccessfulSyncDate(syncKey)?.let {
            LBSyncProcessStatus.SyncSuccessfully(Date(it))
        } ?: LBSyncProcessStatus.NeverSync
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
     * How to fetch the data from the server
     * **WARNING** : Data returned must be ordered by updatedAt
     *
     * @param page: current page value
     * @param sinceLastDate: date to get only the last records
     * @param completion: data & information returned by the server
     */
    protected abstract fun fetchRequest(
        page: Int = 0,
        cursor: String? = null,
        sinceLastDate: Date?,
        completion: LBSyncManagerFetchCompletion<ServerData, PageInfo>,
    )

    /**
     * How to get the updatedAt information from the record coming from the server
     * @param obj: server object
     * @return the nullable updatedAt date
     */
    protected abstract fun updatedAt(obj: ServerData): Date?

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
     * How to upload objects to the server
     * **WARNING** : Must be in a backgroundThread to avoid block the UI
     * @param objects: the object list to push
     * @param completion: provide function success and nullable Exception
     */
    protected abstract suspend fun pushObjectsToServer(
        objects: List<LocalData>,
        completion: (error: Exception?) -> Unit,
    )

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
     * Start the listener for the server notifications
     * eg: Start the Parse LiveQuery
     */
    open fun startServerNotificationListener(): Task<Boolean> {
        val task = TaskCompletionSource<Boolean>()
        task.setResult(true)
        return task.task
    }

    /**
     * Stop the listener for the server notifications
     * eg: stop the Parse LiveQuery
     */
    open fun stopServerNotificationListener(): Task<Boolean> {
        val task = TaskCompletionSource<Boolean>()
        task.setResult(true)
        return task.task
    }
    /*================
     * Public methods
     ================*/

    /**
     * Add a LBSyncToken to get notify for every sync manager status changes
     * @param block: the completion block to execute at every sync manager status changes. The block
     * might be call from background thread.
     * @return the LBSyncToken associated
     */
    fun observe(block: LBSyncChangeStatusClosure): LBSyncToken {
        val token = LBSyncToken(this, block)
        syncObservers.add(token)
        return token
    }

    /**
     * Remove a specific LBSyncToken
     * @param token: the LBSyncToken to remove
     */
    fun removeObserver(token: LBSyncToken) {
        syncObservers.remove(token)
    }

    /**
     * Invalidate the requestId to cancel every current synchronization
     */
    fun cancelAllRequests() {
        requestId = null

        retryTimer?.cancel()
        retryTimer = null
        retryTimerTask?.cancel()
        retryTimerTask = null
    }

    /**
     * Synchronize the sync manager and return the Bolt Task associated
     */
    fun synchronize(): Task<Boolean> {
        val task = TaskCompletionSource<Boolean>()
        synchronize { error ->
            error?.let(task::setError) ?: task.setResult(error == null)
        }
        return task.task
    }

    /**
     * Synchronize the manager and wait for the result
     */
    suspend fun synchronizeWait(): LBResult<Unit> {
        return suspendCoroutine { continuation ->
            synchronize { error ->
                if (error != null) {
                    continuation.resume(LBResult.Failure(error))
                } else {
                    continuation.resume(LBResult.Success(Unit))
                }
            }
        }
    }

    @Deprecated("use synchronize(completion: (error: Exception?) -> Unit)")
    fun synchronize(completion: ((succeeded: Boolean, error: Exception?) -> Unit)) {
        synchronize { error ->
            if (error == null) {
                completion(true, null)
            } else {
                completion(false, error)
            }
        }
    }

    /**
     * Synchronize the sync manager
     * Download data then upload data if needed
     * @param completion: provide function success and nullable Exception
     */
    fun synchronize(completion: (error: Exception?) -> Unit) {
        if (currentSyncStatus.isProcessing()) {
            logger?.v("Synchronization has been cancelled as another is still running...")
            syncIsDirty = true
            completion.invoke(null)
        } else {
            GlobalScope.launch(Dispatchers.IO) {
                download { downloadSucceeded, downloadError ->
                    if (downloadSucceeded) {
                        upload { uploadSucceeded, uploadError ->
                            if (uploadSucceeded) {
                                currentSyncStatus = LBSyncProcessStatus.SyncSuccessfully(Date())
                                completion.invoke(null)
                                if (syncIsDirty) {
                                    synchronize()
                                }
                            } else {
                                completion.invoke(
                                    uploadError
                                        ?: Exception("Something wrong happen during upload"),
                                )
                            }
                        }
                    } else {
                        completion.invoke(
                            downloadError
                                ?: Exception("Something wrong happen during download"),
                        )
                    }
                }
            }
        }
    }

    /**
     * Reset the sync manager
     * Clear the data, the sync status and dates timestamp
     */
    suspend fun resetData() {
        resetTimeStamp()
        clearData()
        currentSyncStatus = LBSyncProcessStatus.NeverSync
        syncIsDirty = false
    }

    fun resetSyncStatus() {
        currentSyncStatus = LBSyncProcessStatus.NeverSync
        syncIsDirty = false
    }

    suspend fun resetTimeStamp() {
        cancelAllRequests()
        timestampStore.clear(syncKey)
        logger?.v("Reset last updated date")
    }
    /*=================
     * Private methods
     =================*/

    /**
     * Generate the retry task
     */
    private fun retryTimerTask(): TimerTask = object : TimerTask() {
        override fun run() {
            logger?.v("Retry to sync, next attempt will be in $retryTempoInMs ms")
            synchronize()
        }
    }

    /**
     * Save the last download date in the timestamp store.
     * @param date A date from the server to save, can be null
     * Automatically save the device date alongside it.
     */
    private suspend fun saveDownloadDate(date: Date?) {
        timestampStore.saveSyncDates(
            syncKey = syncKey,
            serverDateMillis = date?.time,
            localDateMillis = Date().time,
        )
    }

    /**
     * Get the last download date (server version) from the timestamp store.
     * @return the nullable last server download date
     */
    private suspend fun lastServerUpdatedDate(): Date? =
        timestampStore.lastServerSyncDate(syncKey)?.let { Date(it) }

    /**
     * Get the last download date (device version) from the timestamp store.
     * @return the nullable last device download date
     */
    @Suppress("MemberVisibilityCanBePrivate")
    public suspend fun lastSuccessfulSyncDate(): Date? =
        timestampStore.lastSuccessfulSyncDate(syncKey)?.let { Date(it) }

    /**
     * Fetch the data
     * Manage paging if needed
     * @param lastUpdatedDate: the nullable last update date
     * @param page: the page number you want to fetch
     * @param requestId: the current request id
     * @param maxDateFromPreviousPage: the max date from the previous page fetch
     * @param updateData: return the objects gotten with page number and date
     * @param completion: is called if fetching has failed/has been canceled/has reached the last page
     */
    private fun fetch(
        lastUpdatedDate: Date?,
        page: Int = 0,
        cursor: String? = null,
        requestId: String,
        maxDateFromPreviousPage: Date? = null,
        updateData: (suspend (List<ServerData>, Int, Date?) -> Unit)?,
        completion: ((maxDate: Date?, hasBeenCanceled: Boolean, error: Exception?) -> Unit)?,
    ) {
        fetchRequest(page, cursor, lastUpdatedDate) { objects, error, pageInfo, newCursor ->
            when {
                this.requestId != requestId -> completion?.invoke(null, true, null)

                objects != null -> GlobalScope.launch {
                    val updatedDates: List<Date> = objects.mapNotNull(this@LBSyncManager::updatedAt)
                    val maxDate = updatedDates.maxOrNull()
                    val maxTimeDate = arrayListOf(
                        maxDate,
                        maxDateFromPreviousPage,
                        lastUpdatedDate,
                    ).mapNotNull { it }.maxOrNull()
                    updateData?.invoke(objects, page, maxTimeDate)
                    GlobalScope.launch(Dispatchers.IO) {
                        if (hasNextPage(objects.size, pageInfo)) {
                            fetch(
                                lastUpdatedDate,
                                page + 1,
                                newCursor,
                                requestId,
                                maxTimeDate,
                                updateData,
                                completion,
                            )
                        } else {
                            completion?.invoke(maxTimeDate, false, null)
                        }
                    }
                }

                else -> completion?.invoke(null, false, error)
            }
        }
    }

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

    /**
     * Upload the data to the server
     * If nothing to upload, completion success without doing anything
     * @param completion: provide function success and nullable Exception
     */
    private fun upload(completion: ((succeeded: Boolean, error: Exception?) -> Unit)? = null) {
        GlobalScope.launch(Dispatchers.IO) {
            val objects = objectToBeUploaded()
            if (objects.isEmpty()) {
                // nothing to do
                completion?.invoke(true, null)
            } else {
                currentSyncStatus = LBSyncProcessStatus.UploadStarted(Date())
                pushObjectsToServer(objects) { error ->
                    GlobalScope.launch(Dispatchers.IO) {
                        if (error != null) {
                            currentSyncStatus = LBSyncProcessStatus.UploadFinishWithError(
                                error,
                                Date(),
                            )
                            syncIsDirty = true
                            completion?.invoke(false, error)
                        } else {
                            currentSyncStatus = LBSyncProcessStatus.UploadFinishSuccessfully(
                                objects.size,
                                Date(),
                            )
                            if (supportChangeNotificationFromServer()) {
                                completion?.invoke(true, null)
                            } else {
                                download(completion)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Download data from the server
     * Call the fetch() method to do the work
     * @param completion: provide function success and nullable Exception
     */
    private suspend fun download(completion: ((succeeded: Boolean, error: Exception?) -> Unit)? = null) {
        syncIsDirty = false
        val requestId = UUID.randomUUID().toString()
        this.requestId = requestId

        currentSyncStatus = LBSyncProcessStatus.DownloadStarted(Date())

        val updateData: suspend (List<ServerData>, Int, Date?) -> Unit = { objects, _, lastDate ->
            updateData(objects)
            if (supportIncrementalSync()) {
                saveDownloadDate(lastDate)
            }
            currentSyncStatus = LBSyncProcessStatus.DownloadUpdated(objects.size, Date())
        }

        val fetchCompletion: (lastDate: Date?, hasBeenCanceled: Boolean, error: Exception?) -> Unit = { lastDate, hasBeenCanceled, error ->
            when {
                hasBeenCanceled -> {
                    currentSyncStatus = LBSyncProcessStatus.DownloadFinishSuccessfully(Date())
                    completion?.invoke(true, error)
                }

                error != null -> {
                    currentSyncStatus = LBSyncProcessStatus.DownloadFinishWithError(error, Date())
                    syncIsDirty = true
                    completion?.invoke(false, error)
                }

                else -> {
                    currentSyncStatus = LBSyncProcessStatus.DownloadFinishSuccessfully(Date())
                    GlobalScope.launch(Dispatchers.IO) {
                        saveDownloadDate(lastDate)
                    }
                    completion?.invoke(true, error)
                }
            }
        }

        fetch(
            lastServerUpdatedDate(),
            requestId = requestId,
            updateData = updateData,
            completion = fetchCompletion,
        )
    }

    companion object {
        /**
         * Base name of the DataStore Preferences file backing the sync timestamps (matches the legacy
         * SharedPreferences file name so existing installs keep the same on-disk location).
         */
        const val timestampPrefFile: String = "com.lunabee.lbsynchronization"
    }
}

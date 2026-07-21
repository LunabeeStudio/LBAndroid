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

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import studio.lunabee.core.model.LBResult
import studio.lunabee.logger.LBLogger
import studio.lunabee.synchronization.connectivity.LBConnectivityManager
import studio.lunabee.synchronization.connectivity.NetworkState
import studio.lunabee.synchronization.store.LBSyncStorage
import studio.lunabee.synchronization.store.SyncKey
import studio.lunabee.synchronization.syncmanager.LBGenericSyncManager
import studio.lunabee.synchronization.syncmanager.LBSyncProcessStatus
import studio.lunabee.synchronization.syncmanager.LBSyncRefreshEvent
import studio.lunabee.synchronization.syncmanager.defaultSyncScope
import kotlin.reflect.KClass

/**
 * Use LBSyncOperator to manage all sync managers in your app
 * It takes list of LBSyncGroup
 * It can listen for device network changes AND app life cycle
 */
@Suppress("unused")
object LBSyncOperator {

    private lateinit var lastNetworkState: NetworkState
    private var networkListenerJob: Job? = null
    private var appLifecycleJob: Job? = null

    // Lifecycle observation must touch ProcessLifecycleOwner on the main thread. Lazy so merely touching
    // the operator (e.g. triggerRefresh in JVM host tests) never forces Dispatchers.Main to load.
    private val mainScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    val groups: LinkedHashMap<String, LBSyncGroup> = LinkedHashMap()

    /**
     * Call this to let the LBSyncOperator refresh the sync managers for network changes. The refresh is
     * performed when a reconnection is detected (new state is connected AND the previous state was not),
     * by collecting [LBConnectivityManager.observeNetworkStates] in the shared [defaultSyncScope].
     *
     * **WARNING** : A sync manager can only be refreshed if its group carries
     * [LBSyncRefreshEvent.InternetIsBack].
     */
    fun initNetworkListener(context: Context) {
        val appContext = context.applicationContext
        lastNetworkState = LBConnectivityManager.getNetworkState(appContext)
        networkListenerJob?.cancel()
        networkListenerJob = defaultSyncScope.launch {
            LBConnectivityManager.observeNetworkStates(appContext).collect { networkState ->
                if (networkState.isConnected) {
                    networkLogger.v("Internet is available with transport ${networkState.connectionType}")
                    if (!lastNetworkState.isConnected) {
                        triggerRefresh(LBSyncRefreshEvent.InternetIsBack::class)
                    }
                } else {
                    networkLogger.v("Internet is disabled")
                }
                lastNetworkState = networkState
            }
        }
    }

    /**
     * Call this to let the LBSyncOperator refresh the sync managers when the app enters the foreground.
     * Observes [ProcessLifecycleOwner]'s lifecycle as a [Flow] (no broadcasts): a foreground transition
     * triggers the refresh and starts the server-notification listeners; a background transition stops
     * them.
     *
     * **WARNING** : A sync manager can only be refreshed if its group carries
     * [LBSyncRefreshEvent.AppForeground].
     */
    fun initAppLifecycleListener() {
        appLifecycleJob?.cancel()
        appLifecycleJob = mainScope.launch {
            appForegroundFlow()
                .distinctUntilChanged()
                .collect { isForeground ->
                    if (isForeground) {
                        triggerRefresh(LBSyncRefreshEvent.AppForeground::class)
                        startServerNotificationListeners()
                    } else {
                        stopServerNotificationListeners()
                    }
                }
        }
    }

    private fun appForegroundFlow(): Flow<Boolean> = callbackFlow {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                trySend(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                trySend(false)
            }
        }
        lifecycle.addObserver(observer)
        awaitClose { lifecycle.removeObserver(observer) }
    }

    fun syncManagers(): List<LBGenericSyncManager> = groups.values.flatMap { it.syncManagers }

    inline fun <reified T> syncManager(): T? = syncManagers().firstOrNull { it is T }?.let { it as T }

    /**
     * Synchronize every managed [LBSyncGroup] sequentially, in registration order.
     *
     * Each group always attempts (a failing group never short-circuits the following ones), and the
     * per-group failures are aggregated:
     * - no failure → [LBResult.Success];
     * - exactly one failure → [LBResult.Failure] carrying that group's error;
     * - several failures → [LBResult.Failure] carrying an [LBSyncAggregateException] exposing all errors.
     *
     * @return the combined synchronization result across all groups.
     */
    suspend fun syncAllManagers(): LBResult<Unit> = runGroupsSequentially(groups.values)

    private suspend fun runGroupsSequentially(groups: Collection<LBSyncGroup>): LBResult<Unit> {
        val errors: MutableList<Throwable> = mutableListOf()
        for (group in groups) {
            (group.syncManagers() as? LBResult.Failure)?.throwable?.let { errors += it }
        }
        return when (errors.size) {
            0 -> LBResult.Success(Unit)
            1 -> LBResult.Failure(errors.first())
            else -> LBResult.Failure(LBSyncAggregateException(errors = errors))
        }
    }

    internal fun groupsForEvent(eventType: KClass<out LBSyncRefreshEvent>): List<LBSyncGroup> =
        groups.values.filter { group ->
            group.refreshEvents.any { event ->
                event::class == eventType && event.isDelayElapsed(group.lastSuccessfulSync)
            }
        }

    internal fun triggerRefresh(eventType: KClass<out LBSyncRefreshEvent>) {
        val availableGroups = groupsForEvent(eventType)
        availableGroups.flatMap { it.syncManagers }.forEach {
            it.setStatusInternal(LBSyncProcessStatus.PendingSync)
        }
        defaultSyncScope.launch { runGroupsSequentially(availableGroups) }
    }

    /**
     * Start all available server notifications listeners of every managed group, sequentially.
     */
    suspend fun startServerNotificationListeners() {
        groups.values.forEach { it.startServerNotificationListeners() }
    }

    /**
     * Stop all available server notifications listeners of every managed group, sequentially.
     */
    suspend fun stopServerNotificationListeners() {
        groups.values.forEach { it.stopServerNotificationListeners() }
    }

    suspend fun hasSomethingToUpload(): Boolean =
        syncManagers().any { it.hasSomethingToUpload() }

    /**
     * Reset the timestamp of all sync managers by wiping the installed [LBSyncStorage] backend.
     */
    suspend fun resetAllTimestamps() {
        cancelAllRequests()
        LBSyncStorage.requireStore().clearAll()
        logger.v("Reset all SM last updated date")
    }

    /**
     * Seed the status of all sync managers currently added in [groups] from their persisted last
     * successful sync date. Call this once (e.g. at startup); until then every status is
     * [LBSyncProcessStatus.NeverSync].
     */
    suspend fun loadAllStatuses() {
        syncManagers().forEach { it.load() }
    }

    suspend fun resetAllData() {
        syncManagers().forEach { manager ->
            manager.resetData()
        }
    }

    /**
     * Reset status to [LBSyncProcessStatus.NeverSync] of all sync managers currently added in [groups]
     */
    fun resetAllSyncStatus() {
        syncManagers().forEach(LBGenericSyncManager::resetSyncStatus)
    }

    fun cancelAllRequests() {
        syncManagers().forEach(LBGenericSyncManager::cancelAllRequests)
    }

    /**
     * Combine the [LBSyncProcessStatus] of every managed manager (across all [groups]) into a single map
     * keyed by [LBGenericSyncManager.syncKey]. The map carries the latest status of each member and
     * re-emits on every member transition.
     *
     * Registry snapshot: the member set is read once, when collection starts. A manager (or group) added
     * AFTER a collection has begun is NOT picked up by that already-running collection — re-collect this
     * flow to observe a newly-registered manager.
     *
     * syncKey collision: two managers sharing the same [LBGenericSyncManager.syncKey] collide in the map
     * (last one wins), so duplicate keys silently drop members from the combined view.
     *
     * @return a flow of member statuses keyed by `syncKey`; emits [emptyMap] once when no manager is
     * registered (a `combine` over an empty set of flows would otherwise never emit).
     */
    fun statusByKey(): Flow<Map<SyncKey, LBSyncProcessStatus>> = flow {
        val managers = groups.values.flatMap { it.syncManagers }
        if (managers.isEmpty()) {
            emitAll(flowOf(emptyMap()))
        } else {
            emitAll(
                combine(managers.map { manager -> manager.status.map { manager.syncKey to it } }) {
                    it.toMap()
                },
            )
        }
    }

    /**
     * Derived from [statusByKey]: `true` while ANY managed manager status
     * [LBSyncProcessStatus.isProcessing], and `false` once every manager is idle. Consecutive duplicate
     * values are dropped via [distinctUntilChanged].
     *
     * Mind [LBSyncProcessStatus.isProcessing]'s documented quirk: the mid-pipeline
     * [LBSyncProcessStatus.UploadFinishSuccessfully] / [LBSyncProcessStatus.DownloadFinishSuccessfully]
     * steps count as processing.
     *
     * Registry snapshot: the member set is read once, when collection starts. A manager (or group) added
     * AFTER a collection has begun is NOT picked up by that already-running collection — re-collect this
     * flow to observe a newly-registered manager.
     *
     * syncKey collision: two managers sharing the same [LBGenericSyncManager.syncKey] collide in the
     * underlying map (last one wins), so duplicate keys silently drop members from the combined view.
     *
     * @return a flow of the app-wide aggregate syncing state.
     */
    fun isSyncing(): Flow<Boolean> = statusByKey()
        .map { statuses -> statuses.values.any { it.isProcessing() } }
        .distinctUntilChanged()
}

private val logger: Logger = LBLogger.get("$LogTag ${LBSyncOperator::class.simpleName}")
private val networkLogger: Logger = LBLogger.get("$LogTag Network")

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

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
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
import studio.lunabee.synchronization.lifecycle.LBSyncApplication
import studio.lunabee.synchronization.store.syncTimestampStore
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

    // Device network fields
    private lateinit var lastNetworkState: NetworkState
    private var connectivityManager: LBConnectivityManager? = null

    /**
     * Map of LBSyncGroup managed
     */
    val groups: LinkedHashMap<String, LBSyncGroup> = LinkedHashMap()

    /**
     * Broadcast receiver for network changes
     */
    var connectivityBroadcastReceiver: BroadcastReceiver? = null

    /**
     * Broadcast receiver for lifecycle changes : APP_FOREGROUND_ACTION
     */
    var appLifecycleForegroundBroadcastReceiver: BroadcastReceiver? = null

    /**
     * Broadcast receiver for lifecycle changes : APP_BACKGROUND_ACTION
     */
    var appLifecycleBackgroundBroadcastReceiver: BroadcastReceiver? = null

    /**
     * Call this to let the LBSyncOperator refresh the sync managers for network changes
     * The refresh is perform if network change is detected : new state is connected AND old state was not
     * **WARNING** : A sync manager can only be refreshed if it has LBSyncRefreshEvent.INTERNET_IS_BACK
     */
    fun initNetworkListener(context: Context) {
        lastNetworkState = LBConnectivityManager.getNetworkState(context)
        connectivityManager?.stopListening(context)
        connectivityManager = LBConnectivityManager()
        connectivityManager?.listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                context?.let {
                    val networkState = LBConnectivityManager.getNetworkState(it)
                    if (networkState.isConnected && networkState.connectionType != null) {
                        networkLogger.v("Internet is available with type ${networkState.connectionType}")
                        if (!lastNetworkState.isConnected) {
                            triggerRefresh(LBSyncRefreshEvent.InternetIsBack::class)
                        }
                    } else {
                        networkLogger.v("Internet is disable")
                    }
                    lastNetworkState = networkState
                }
            }
        }
        connectivityManager?.startListening(context)
    }

    /**
     * Call this to let the LBSyncOperator refresh the sync managers when app enter in foreground
     * **WARNING** : A sync manager can only be refreshed if it has [LBSyncRefreshEvent.AppForeground]
     *
     * This function also start and stop serverNotificationListeners available
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun initAppLifecycleListener(context: Context) {
        // Foreground
        appLifecycleForegroundBroadcastReceiver?.let(context::unregisterReceiver)
        appLifecycleForegroundBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                triggerRefresh(LBSyncRefreshEvent.AppForeground::class)
                defaultSyncScope.launch { startServerNotificationListeners() }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.registerReceiver(
                appLifecycleForegroundBroadcastReceiver,
                IntentFilter(LBSyncApplication.APP_FOREGROUND_ACTION),
                Context.RECEIVER_EXPORTED,
            )
        } else {
            context.registerReceiver(
                appLifecycleForegroundBroadcastReceiver,
                IntentFilter(LBSyncApplication.APP_FOREGROUND_ACTION),
            )
        }
        // Background
        appLifecycleBackgroundBroadcastReceiver?.let(context::unregisterReceiver)
        appLifecycleBackgroundBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                defaultSyncScope.launch { stopServerNotificationListeners() }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.registerReceiver(
                appLifecycleBackgroundBroadcastReceiver,
                IntentFilter(LBSyncApplication.APP_BACKGROUND_ACTION),
                Context.RECEIVER_EXPORTED,
            )
        } else {
            context.registerReceiver(
                appLifecycleBackgroundBroadcastReceiver,
                IntentFilter(LBSyncApplication.APP_BACKGROUND_ACTION),
            )
        }
    }

    /**
     * @return all the sync managers managed
     */
    fun syncManagers(): List<LBGenericSyncManager> = groups.values.flatMap { it.syncManagers }

    /**
     * @return sync manager of type T if available in sync managers managed
     */
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
     * This intentionally fixes the legacy chained-continuation behavior that dropped every group's error
     * but the last.
     *
     * @return the combined synchronization result across all groups.
     */
    suspend fun syncAllManagers(): LBResult<Unit> = runGroupsSequentially(groups.values)

    /**
     * Synchronize the given [groups] sequentially in iteration order, awaiting each
     * [LBSyncGroup.syncManagers]. Every group always attempts; failures are collected then combined per
     * the [syncAllManagers] aggregation rule.
     *
     * @param groups the groups to synchronize, in the desired sequential order.
     * @return the combined synchronization result.
     */
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

    /**
     * @param eventType the refresh-event type that fired.
     * @return the managed groups carrying a matching [LBSyncRefreshEvent] whose debounce delay has elapsed
     * against the group's [LBSyncGroup.lastSuccessfulSync].
     */
    internal fun groupsForEvent(eventType: KClass<out LBSyncRefreshEvent>): List<LBSyncGroup> =
        groups.values.filter { group ->
            group.refreshEvents.any { event ->
                event::class == eventType && event.isDelayElapsed(group.lastSuccessfulSync)
            }
        }

    /**
     * Mark the managers of every group matching [eventType] as [LBSyncProcessStatus.PendingSync] and
     * launch their sequential synchronization detached in the shared library [defaultSyncScope].
     *
     * @param eventType the refresh-event type that fired.
     */
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

    /**
     * @return true if any sync manager has data to upload
     */
    suspend fun hasSomethingToUpload(): Boolean =
        syncManagers().any { it.hasSomethingToUpload() }

    /**
     * Reset the timestamp of all sync managers by wiping the shared timestamp store.
     *
     * @param context A valid context used to access the timestamp store
     */
    suspend fun resetAllTimestamps(context: Context) {
        cancelAllRequests()
        context.syncTimestampStore.clearAll()
        logger.v("Reset all SM last updated date")
    }

    /**
     * Seed the status of all sync managers currently added in [groups] from their persisted last
     * successful sync date. Call this once (e.g. at startup) since the status is no longer seeded
     * synchronously in each manager's constructor.
     */
    suspend fun loadAllStatuses() {
        syncManagers().forEach { it.load() }
    }

    /**
     * Reset the data of all sync managers currently added in [groups]
     */
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

    /**
     * Cancel all requests for all sync managers
     */
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
    fun statusByKey(): Flow<Map<String, LBSyncProcessStatus>> = flow {
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

private val logger: Logger = LBLogger.get("LBSM ${LBSyncOperator::class.simpleName}")
private val networkLogger: Logger = LBLogger.get("LBSM Network")

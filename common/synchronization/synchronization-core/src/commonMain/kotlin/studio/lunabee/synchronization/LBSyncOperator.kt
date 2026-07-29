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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Job
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
import studio.lunabee.synchronization.store.LBSyncStorage
import studio.lunabee.synchronization.store.SyncKey
import studio.lunabee.synchronization.syncmanager.LBGenericSyncManager
import studio.lunabee.synchronization.syncmanager.LBSyncProcessStatus
import studio.lunabee.synchronization.syncmanager.LBSyncRefreshEvent
import studio.lunabee.synchronization.syncmanager.LBSyncRefreshEventData
import studio.lunabee.synchronization.syncmanager.defaultSyncScope
import kotlin.reflect.KClass

/**
 * Use LBSyncOperator to manage all sync managers in your app
 * It takes list of LBSyncGroup
 * It can listen for device network changes AND app life cycle
 */
@Suppress("unused")
object LBSyncOperator {

    val groups: LinkedHashMap<String, LBSyncGroup> = LinkedHashMap()

    private val registeredListeners: MutableList<Job> = mutableListOf()

    /**
     * Registers listeners that will be used to trigger refreshes of groups related to the emitted events.
     * (see [LBSyncGroup.refreshEvents]).
     */
    fun registerEventListeners(
        listeners: List<LBSyncEventListener<*>>,
    ) {
        unregisterListeners()
        listeners.forEach { listener ->
            registeredListeners += listener.register(
                onEvent = ::triggerRefresh,
            )
        }
    }

    private fun unregisterListeners() {
        registeredListeners.forEach { it.cancel() }
        registeredListeners.clear()
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

    internal suspend fun triggerRefresh(
        data: LBSyncRefreshEventData,
    ) {
        if (shouldRefresh(data = data)) {
            val availableGroups = groupsForEvent(data.type)
            availableGroups.flatMap { it.syncManagers }.forEach {
                it.setStatusInternal(LBSyncProcessStatus.PendingSync)
            }
            defaultSyncScope.launch { runGroupsSequentially(availableGroups) }
        }
        handleEventData(data = data)
    }

    /**
     * Given an [LBSyncRefreshEventData], checks if the we should refresh sync managers.
     */
    private fun shouldRefresh(data: LBSyncRefreshEventData): Boolean =
        when (data) {
            is LBSyncRefreshEventData.AppForeground -> data.isForeground
            LBSyncRefreshEventData.InternetIsBack -> true
        }

    /**
     * Additional action to do depending on a given [LBSyncRefreshEventData]
     */
    private suspend fun handleEventData(data: LBSyncRefreshEventData) {
        when (data) {
            is LBSyncRefreshEventData.AppForeground -> if (data.isForeground) {
                startServerNotificationListeners()
            } else {
                stopServerNotificationListeners()
            }

            LBSyncRefreshEventData.InternetIsBack -> {
                // no-op
            }
        }
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

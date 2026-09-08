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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * Refreshes can be triggered by events emitted from registered [LBSyncEventListener] (see [registerEventListeners])
 *
 * **Single entry point.** Every sync request goes through this operator: [syncAllManagers] for the whole
 * registry, [sync] for one group or one manager (by instance, or by type with `sync<MyManager>()`). [LBSyncGroup.syncManagers] and
 * [LBGenericSyncManager.synchronize] are `internal`, so a consumer cannot start a run behind the
 * operator's back and the operator stays in charge of ordering.
 *
 * **Ordering.** Requests are serialized: a request waits for the sync currently running to finish before
 * it starts. Inside one [syncAllManagers] (or event-triggered) run, groups run sequentially in
 * registration order and the managers of a group run in parallel. So a manager synchronized directly
 * through [sync] never overlaps a full run, and dependencies modelled as "earlier group" hold for direct
 * requests too.
 *
 * Two runs still escape the serialization, both by design:
 * - the automatic retry of a failed run (see [studio.lunabee.synchronization.runner.SyncRunner]), which is
 *   detached from any caller — set [LBGenericSyncManager.retryTempo] to `null` on a manager whose retry
 *   must not run out of order. Enqueueing a request pre-empts the pending retry of the managers it
 *   targets right away (before waiting for the lock), so a parked retry cannot fire in front of a request
 *   that is already queued; a retry scheduled *after* that, by a run failing while the request waits, is
 *   pre-empted when the request finally reaches the runner;
 * - a run started from inside a manager's own SPI. Never call [sync] / [syncAllManagers] from
 *   `fetchRequest`, `pushObjectsToServer` or another engine callback: the calling coroutine already owns
 *   the sync lock and would deadlock.
 */
@Suppress("unused")
object LBSyncOperator {

    val groups: LinkedHashMap<String, LBSyncGroup> = LinkedHashMap()

    private val registeredListeners: MutableList<Job> = mutableListOf()

    /**
     * Serializes every sync request routed through the operator, so ordering is decided here and nowhere
     * else. Held for the whole run (all groups of a [syncAllManagers], one group or one manager for
     * [sync]), never while starting/stopping the server-notification listeners.
     */
    private val syncMutex: Mutex = Mutex()

    /**
     * Registers listeners that will be used to trigger refreshes of groups related to the emitted events
     * (see [LBSyncGroup.refreshEvents]). Replaces any previously registered listeners (their jobs are cancelled).
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
     * Suspends until any sync already running through the operator has finished.
     *
     * @return the combined synchronization result across all groups.
     */
    suspend fun syncAllManagers(): LBResult<Unit> {
        groups.values.forEach { it.cancelPendingRetries() }
        return syncMutex.withLock { runGroupsSequentially(groups.values) }
    }

    /**
     * Synchronize a single [LBSyncGroup] — its managers in parallel — without running the other groups.
     *
     * Suspends until any sync already running through the operator has finished, so the group never
     * overlaps a [syncAllManagers] run.
     *
     * @param group the group to synchronize. It does not have to be registered in [groups].
     * @return the group's combined synchronization result.
     */
    suspend fun sync(group: LBSyncGroup): LBResult<Unit> {
        group.cancelPendingRetries()
        return syncMutex.withLock { group.syncManagers() }
    }

    /**
     * Synchronize a single manager, without running the other managers of its group.
     *
     * This is the public route to a manager's pipeline ([LBGenericSyncManager.synchronize] itself is
     * `internal`). Suspends until any sync already running through the operator has finished, so the
     * manager never overlaps a group or full run.
     *
     * @param manager the manager to synchronize. It does not have to be registered in [groups].
     * @return the manager's synchronization result.
     */
    suspend fun sync(manager: LBGenericSyncManager): LBResult<Unit> {
        manager.cancelPendingRetry()
        return syncMutex.withLock { manager.synchronize() }
    }

    /**
     * Synchronize the first registered manager of type [T], as [sync] does — the shorthand for
     * `syncManager<T>()` followed by `sync(manager)`.
     *
     * @param T the manager type to look up in [groups], matched as [syncManager] does (first registered
     * manager that is a [T]).
     * @return the manager's synchronization result, or [LBResult.Failure] carrying an
     * [IllegalArgumentException] when no manager of that type is registered.
     */
    suspend inline fun <reified T : LBGenericSyncManager> sync(): LBResult<Unit> {
        val manager: T? = syncManager<T>()
        return manager
            ?.let { sync(manager = it) }
            ?: LBResult.Failure(IllegalArgumentException("No ${T::class.simpleName} registered in LBSyncOperator.groups"))
    }

    /**
     * Synchronize the registered group stored under [name], as [sync] does.
     *
     * @param name the [groups] key of the group to synchronize.
     * @return the group's combined synchronization result, or [LBResult.Failure] carrying an
     * [IllegalArgumentException] when no group is registered under [name].
     */
    suspend fun syncGroup(name: String): LBResult<Unit> = groups[name]
        ?.let { group -> sync(group = group) }
        ?: LBResult.Failure(IllegalArgumentException("No LBSyncGroup registered under the key \"$name\""))

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
            defaultSyncScope.launch {
                availableGroups.forEach { it.cancelPendingRetries() }
                syncMutex.withLock { runGroupsSequentially(availableGroups) }
            }
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

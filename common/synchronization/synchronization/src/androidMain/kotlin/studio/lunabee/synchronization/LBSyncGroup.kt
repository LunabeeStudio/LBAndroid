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

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import studio.lunabee.core.model.LBResult
import studio.lunabee.synchronization.syncmanager.LBGenericSyncManager
import studio.lunabee.synchronization.syncmanager.LBSyncProcessStatus
import studio.lunabee.synchronization.syncmanager.LBSyncRefreshEvent
import kotlin.time.Instant

/**
 * Use this to group sync managers together.
 * Sync managers within the same group will be synchronized in parallel.
 * Dependency between table can be manage using group.
 *
 * @param syncManagers: The sync managers managed by this group
 * @param refreshEvents: The refresh events list this group must be aware of
 */
@Suppress("unused")
class LBSyncGroup(
    var syncManagers: LinkedHashSet<LBGenericSyncManager> = linkedSetOf(),
    var refreshEvents: List<LBSyncRefreshEvent> = emptyList(),
) {

    /**
     * Use this to gate the whole group's synchronization. Evaluated exactly once per [syncManagers]
     * attempt; when it returns false every manager is marked [LBSyncProcessStatus.Disabled] and the
     * attempt fails with [LBSyncClosureException].
     *
     * Being a suspend closure, it subsumes both the legacy synchronous and asynchronous enablement
     * checks (e.g. gate the sync on a session call) without any callback bridging.
     */
    var isEnabled: suspend () -> Boolean = { true }

    /**
     * The lastSuccessfulSync of the oldest successfully synchronized sync manager or
     * [Instant.fromEpochMilliseconds] of 0 if one of the sync manager was not synchronized successfully.
     */
    internal val lastSuccessfulSync: Instant
        get() {
            return syncManagers.minOfOrNull {
                (it.currentSyncStatus as? LBSyncProcessStatus.SyncSuccessfully)?.lastSuccessfulSync
                    ?: Instant.fromEpochMilliseconds(0)
            } ?: Instant.fromEpochMilliseconds(0)
        }

    /**
     * Synchronize all the managers of the group in parallel.
     *
     * The [isEnabled] gate is evaluated exactly once: when it returns false every manager is marked
     * [LBSyncProcessStatus.Disabled] and the result is [LBResult.Failure] carrying an
     * [LBSyncClosureException].
     *
     * Otherwise every manager runs to completion via `async`/`awaitAll` — because each
     * [LBGenericSyncManager.synchronize] returns its failure as a value, a failing sibling never
     * cancels the others (parity with the legacy Bolts `whenAll`). The per-manager results are then
     * combined:
     * - no failure → [LBResult.Success];
     * - exactly one failure → [LBResult.Failure] carrying that manager's error;
     * - several failures → [LBResult.Failure] carrying an [LBSyncAggregateException] exposing all errors.
     *
     * @return the combined synchronization result.
     */
    suspend fun syncManagers(): LBResult<Unit> {
        if (!isEnabled()) {
            syncManagers.forEach { it.setStatusInternal(LBSyncProcessStatus.Disabled) }
            return LBResult.Failure(LBSyncClosureException())
        }

        val results: List<LBResult<Unit>> = coroutineScope {
            syncManagers
                .map { manager -> async { manager.synchronize() } }
                .awaitAll()
        }

        val errors: List<Throwable> = results.mapNotNull { (it as? LBResult.Failure)?.throwable }

        return when (errors.size) {
            0 -> LBResult.Success(Unit)
            1 -> LBResult.Failure(errors.first())
            else -> LBResult.Failure(LBSyncAggregateException(errors = errors))
        }
    }

    /**
     * Start every available server notification listener of the group, filtering on
     * [LBGenericSyncManager.supportChangeNotificationFromServer]. Listeners are started in parallel.
     */
    suspend fun startServerNotificationListeners() {
        coroutineScope {
            syncManagers
                .filter { it.supportChangeNotificationFromServer() }
                .map { manager -> async { manager.startServerNotificationListener() } }
                .awaitAll()
        }
    }

    /**
     * Stop every available server notification listener of the group, filtering on the same
     * [LBGenericSyncManager.supportChangeNotificationFromServer] set as
     * [startServerNotificationListeners]. Listeners are stopped in parallel.
     */
    suspend fun stopServerNotificationListeners() {
        coroutineScope {
            syncManagers
                .filter { it.supportChangeNotificationFromServer() }
                .map { manager -> async { manager.stopServerNotificationListener() } }
                .awaitAll()
        }
    }

    /**
     * @return true if any sync manager of the group has data to upload
     */
    suspend fun hasSomethingToUpload(): Boolean =
        syncManagers.any { it.hasSomethingToUpload() }

    /**
     * Reset the timeStamp of all sync managers of the group
     */
    suspend fun resetAllTimestamps() {
        syncManagers.forEach { it.resetTimeStamp() }
    }

    /**
     * Reset the data of all sync managers of the group
     */
    suspend fun resetAllData() {
        syncManagers.forEach { manager ->
            manager.resetData()
        }
    }

    /**
     * Cancel all requests for all sync managers of the group
     */
    fun cancelAllRequests() {
        syncManagers.forEach(LBGenericSyncManager::cancelAllRequests)
    }
}

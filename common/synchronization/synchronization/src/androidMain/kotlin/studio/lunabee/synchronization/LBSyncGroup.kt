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

import bolts.Task
import bolts.TaskCompletionSource
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import studio.lunabee.core.model.LBResult
import studio.lunabee.synchronization.syncmanager.LBGenericSyncManager
import studio.lunabee.synchronization.syncmanager.LBSyncProcessStatus
import studio.lunabee.synchronization.syncmanager.LBSyncRefreshEvent
import java.util.Date

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
     * Use this to enable the sync for specifics conditions
     * example : you can enable the sync only if user is logged in
     */
    var isEnableClosure: () -> Boolean = {
        true
    }

    /**
     * Use this to enable the sync for specifics conditions that need to be computed asynchronously
     * example : you can enable the sync only if a call to a web service succeed
     * */
    var isEnableClosureAsync: suspend (completion: (Boolean) -> Unit) -> Unit = { completion ->
        completion(true)
    }

    /**
     * The lastSuccessfulSync of the oldest successfully synchronized sync manager or Date(0) if one of the sync manager was not
     * synchronized successfully.
     */
    internal val lastSuccessfulSync: Date
        get() {
            return syncManagers.minOfOrNull {
                (it.currentSyncStatus as? LBSyncProcessStatus.SyncSuccessfully)?.lastSuccessfulSync ?: Date(0)
            } ?: Date(0)
        }

    /**
     * Synchronize all the managers of the group
     * @param completion: provide nullable Exception
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun syncManagers(completion: ((error: Exception?) -> Unit)? = null) {
        val task = syncManagerTask()
        task.continueWith(
            { continueTask: Task<Void>? ->
                completion?.invoke(continueTask?.error)
            },
            Task.UI_THREAD_EXECUTOR,
        )
    }

    /**
     * Synchronize all the managers of the group
     * ⚠ This is caller responsibility to switch coroutine context
     */
    suspend fun syncManagers(): LBResult<Unit> {
        val task = syncManagerTask()
        task.waitForCompletion()
        return if (task.isFaulted) {
            LBResult.Failure(task.error)
        } else {
            LBResult.Success(Unit)
        }
    }

    /**
     * @return the Bolt Task to sync all the managers of the group
     */
    fun syncManagerTask(): Task<Void> {
        val task = getTasksIfClosureIsEnabled {
            Task.whenAll(syncManagers.map(LBGenericSyncManager::synchronize))
        }

        if (task.isCompleted && task.result == null) {
            syncManagers
                .filter { it.currentSyncStatus != LBSyncProcessStatus.Disabled }
                .forEach {
                    it.currentSyncStatus = LBSyncProcessStatus.Disabled
                }
        }

        return task
    }

    /**
     * @return the Bolt Task to start all the serverNotificationListener available of the group
     */
    fun startServerNotificationListeners(): Task<Void> {
        return if (syncManagers.any { it.supportChangeNotificationFromServer() }) {
            getTasksIfClosureIsEnabled {
                Task.whenAll(
                    syncManagers.filter { it.supportChangeNotificationFromServer() }
                        .map(LBGenericSyncManager::startServerNotificationListener),
                )
            }
        } else {
            Task.forResult(null)
        }
    }

    /**
     * @return the Bolt Task given only if isEnableClosure and isEnableClosureAsync allows it.
     */
    private fun getTasksIfClosureIsEnabled(getTaskToFollow: () -> Task<Void>): Task<Void> {
        return if (isEnableClosure()) {
            val taskIsEnabledClosureAsync = TaskCompletionSource<Boolean>()
            GlobalScope.launch {
                isEnableClosureAsync { isEnable ->
                    if (isEnable) {
                        taskIsEnabledClosureAsync.setResult(isEnable)
                    } else {
                        syncManagers.forEach {
                            it.currentSyncStatus = LBSyncProcessStatus.Disabled
                        }
                        taskIsEnabledClosureAsync.setError(LBSyncClosureException())
                    }
                }
            }
            taskIsEnabledClosureAsync.task.onSuccessTask {
                if (isEnableClosure()) {
                    getTaskToFollow()
                } else {
                    Task.forError(LBSyncClosureException())
                }
            }
        } else {
            Task.forError(LBSyncClosureException())
        }
    }

    /**
     * @return the Bolt Task to stop all the serverNotificationListener available of the group
     */
    fun stopServerNotificationListeners(): Task<Void> {
        val tasks = syncManagers.filter(LBGenericSyncManager::supportChangeNotificationFromServer).map(
            LBGenericSyncManager::stopServerNotificationListener,
        )
        return Task.whenAll(tasks)
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

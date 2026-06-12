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
import bolts.Task
import co.touchlab.kermit.Logger
import studio.lunabee.logger.LBLogger
import studio.lunabee.synchronization.connectivity.LBConnectivityManager
import studio.lunabee.synchronization.connectivity.NetworkState
import studio.lunabee.synchronization.lifecycle.LBSyncApplication
import studio.lunabee.synchronization.store.syncTimestampStore
import studio.lunabee.synchronization.syncmanager.LBGenericSyncManager
import studio.lunabee.synchronization.syncmanager.LBSyncProcessStatus
import studio.lunabee.synchronization.syncmanager.LBSyncRefreshEvent
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
                            syncAllGroupTasks(LBSyncRefreshEvent.InternetIsBack::class)
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
                syncAllGroupTasks(LBSyncRefreshEvent.AppForeground::class)
                startServerNotificationListeners()
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
                stopServerNotificationListeners()
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
     * Sync all LBSyncGroup managed
     * @param eventType a optional event type that triggered the refresh
     * @return return the Bolt Task associated
     */
    private fun syncAllGroupTasks(eventType: KClass<out LBSyncRefreshEvent>? = null): Task<Void>? {
        var currentTask: Task<Void>? = null
        val availableGroups = eventType?.let { refreshEvent ->
            groups.values.filter {
                it.refreshEvents.any { event ->
                    event::class == refreshEvent &&
                        event.isDelayElapsed(it.lastSuccessfulSync)
                }
            }
        } ?: groups.values

        availableGroups.flatMap { it.syncManagers }.forEach {
            it.setStatusInternal(LBSyncProcessStatus.PendingSync)
        }

        for (group in availableGroups) {
            currentTask = if (currentTask != null) {
                currentTask.continueWithTask { group.syncManagerTask() }
            } else {
                group.syncManagerTask()
            }
        }
        return currentTask
    }

    /**
     * Sync all sync managers
     * @param completion: provide nullable Exception
     */
    fun syncAllManagers(completion: ((error: Exception?) -> Unit)? = null) {
        syncAllGroupTasks()?.let {
            it.continueWith(
                { task: Task<Void>? -> completion?.invoke(task?.error) },
                Task.UI_THREAD_EXECUTOR,
            )
        } ?: completion?.invoke(null)
    }

    /**
     * Start all available server notifications listeners
     */
    fun startServerNotificationListeners() {
        var currentTask: Task<Void>? = null
        for (group in groups.values) {
            currentTask = if (currentTask != null) {
                currentTask.continueWithTask { group.startServerNotificationListeners() }
            } else {
                group.startServerNotificationListeners()
            }
        }
    }

    /**
     * Stop all available server notifications listeners
     */
    fun stopServerNotificationListeners() {
        var currentTask: Task<Void>? = null
        for (group in groups.values) {
            currentTask = if (currentTask != null) {
                currentTask.continueWithTask { group.stopServerNotificationListeners() }
            } else {
                group.stopServerNotificationListeners()
            }
        }
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
}

private val logger: Logger = LBLogger.get("LBSM ${LBSyncOperator::class.simpleName}")
private val networkLogger: Logger = LBLogger.get("LBSM Network")

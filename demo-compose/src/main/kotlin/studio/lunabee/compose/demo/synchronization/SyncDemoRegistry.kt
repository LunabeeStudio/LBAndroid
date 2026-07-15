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

package studio.lunabee.compose.demo.synchronization

import android.content.Context
import studio.lunabee.synchronization.LBSyncGroup
import studio.lunabee.synchronization.LBSyncOperator
import studio.lunabee.synchronization.datastore.dataStoreSyncTimestampStore
import studio.lunabee.synchronization.store.LBSyncStorage
import studio.lunabee.synchronization.syncmanager.LBSyncRefreshEvent

/**
 * Application-scoped wiring for the synchronization demo. The local DB, fake server and sync manager
 * live here (not in the ViewModel) so the manager registered in [LBSyncOperator] and the UI observe the
 * exact same instances — a refresh triggered by the operator on app-foreground / internet-back mutates
 * the very data the screen renders.
 *
 * The demo manager is registered in a single [LBSyncGroup]; the refresh-on-foreground / refresh-on-
 * internet-back toggles simply add or remove the built-in [LBSyncRefreshEvent]s from that group.
 */
object SyncDemoRegistry {

    private const val GroupKey: String = "demo"

    @Volatile
    private var initialized: Boolean = false

    lateinit var server: FakeRemoteServer
        private set
    lateinit var localDb: LocalItemDatabase
        private set
    lateinit var syncManager: DemoItemSyncManager
        private set

    private val group: LBSyncGroup = LBSyncGroup()

    /**
     * Build the graph once and register the operator group + lifecycle/network listeners. Safe to call
     * from both [DemoApp] and the ViewModel.
     */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext
            // Install the DataStore-backed cursor store once, before any manager resolves it lazily.
            LBSyncStorage.install(appContext.dataStoreSyncTimestampStore())
            server = FakeRemoteServer.getInstance(appContext)
            localDb = LocalItemDatabase()
            syncManager = DemoItemSyncManager(localDb = localDb, server = server)
            group.syncManagers = linkedSetOf(syncManager)
            LBSyncOperator.groups[GroupKey] = group
            // Built-in events: the operator observes ProcessLifecycleOwner (foreground) and connectivity;
            // the group only reacts to events present in its refreshEvents list.
            LBSyncOperator.initAppLifecycleListener()
            LBSyncOperator.initNetworkListener(appContext)
            initialized = true
        }
    }

    /** Rebuild the group's refresh-event list from the two toggles. */
    fun setRefreshEvents(onForeground: Boolean, onInternetBack: Boolean) {
        group.refreshEvents = buildList {
            if (onForeground) add(LBSyncRefreshEvent.AppForeground())
            if (onInternetBack) add(LBSyncRefreshEvent.InternetIsBack())
        }
    }
}

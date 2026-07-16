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
import dagger.hilt.android.qualifiers.ApplicationContext
import studio.lunabee.synchronization.LBSyncGroup
import studio.lunabee.synchronization.LBSyncOperator
import studio.lunabee.synchronization.room.roomSyncTimestampStore
import studio.lunabee.synchronization.store.LBSyncStorage
import studio.lunabee.synchronization.syncmanager.LBSyncRefreshEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-scoped wiring for the synchronization demo. The local DB, fake server and sync manager are
 * Hilt singletons injected here (and read by the ViewModel), so the manager registered in
 * [LBSyncOperator] and the UI observe the exact same instances — a refresh triggered by the operator on
 * app-foreground / internet-back mutates the very data the screen renders.
 *
 * The demo manager is registered in a single [LBSyncGroup]; the refresh-on-foreground / refresh-on-
 * internet-back toggles simply add or remove the built-in [LBSyncRefreshEvent]s from that group.
 */
@Singleton
class SyncDemoRegistry @Inject constructor(
    @ApplicationContext context: Context,
    val server: FakeRemoteServer,
    val localDb: LocalItemDatabase,
    val syncManager: DemoItemSyncManager,
) {

    private val group: LBSyncGroup = LBSyncGroup()

    init {
        // Install the cursor store once, before any manager resolves it lazily on the first sync.
        LBSyncStorage.install(context.roomSyncTimestampStore())
        group.syncManagers = linkedSetOf(syncManager)
        LBSyncOperator.groups[GroupKey] = group
        // Built-in events: the operator observes ProcessLifecycleOwner (foreground) and connectivity;
        // the group only reacts to events present in its refreshEvents list.
        LBSyncOperator.initAppLifecycleListener()
        LBSyncOperator.initNetworkListener(context)
    }

    fun setRefreshEvents(onForeground: Boolean, onInternetBack: Boolean) {
        group.refreshEvents = buildList {
            if (onForeground) add(LBSyncRefreshEvent.AppForeground())
            if (onInternetBack) add(LBSyncRefreshEvent.InternetIsBack())
        }
    }

    private companion object {
        const val GroupKey: String = "demo"
    }
}

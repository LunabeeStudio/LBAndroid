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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import studio.lunabee.synchronization.connectivity.LBConnectivityManager
import studio.lunabee.synchronization.syncmanager.LBSyncProcessStatus

/**
 * Holds the fake server, local DB and the [DemoItemSyncManager], and exposes the bits the screen needs.
 */
class SyncDemoViewModel(application: Application) : AndroidViewModel(application) {

    private val server: FakeRemoteServer
    private val localDb: LocalItemDatabase
    private val syncManager: DemoItemSyncManager

    init {
        SyncDemoRegistry.init(application)
        server = SyncDemoRegistry.server
        localDb = SyncDemoRegistry.localDb
        syncManager = SyncDemoRegistry.syncManager
    }

    val localItems: StateFlow<List<LocalItem>> = localDb.items
    val serverItems: StateFlow<List<ServerItem>> = server.items
    val status: StateFlow<LBSyncProcessStatus> = syncManager.status

    /** Live device connectivity, driven by the library's modern [LBConnectivityManager] flow. */
    val isOnline: StateFlow<Boolean> = LBConnectivityManager.networkStates(application)
        .map { it.isConnected }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = LBConnectivityManager.getNetworkState(application).isConnected,
        )

    /** Labels of items diverged on both sides; surfaced non-blocking, the sync itself still succeeds. */
    val conflicts: StateFlow<List<String>> = localDb.items
        .map { items -> items.filter { it.isConflicted }.map { it.label } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = emptyList(),
        )

    private val _failNextSync: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val failNextSync: StateFlow<Boolean> = _failNextSync.asStateFlow()

    private val _showDeleted: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val showDeleted: StateFlow<Boolean> = _showDeleted.asStateFlow()

    private val _refreshOnForeground: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val refreshOnForeground: StateFlow<Boolean> = _refreshOnForeground.asStateFlow()

    private val _refreshOnInternetBack: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val refreshOnInternetBack: StateFlow<Boolean> = _refreshOnInternetBack.asStateFlow()

    init {
        // Seed the status from the persisted cursor (NeverSync until this completes).
        viewModelScope.launch { syncManager.load() }
    }

    fun addClientItem() {
        localDb.addLocal("Client item #${localItems.value.size + 1}")
    }

    fun addServerItem() {
        viewModelScope.launch { server.addRemote("Server item #${serverItems.value.size + 1}") }
    }

    fun touchClientItem(id: String) {
        localDb.touch(id)
    }

    fun deleteClientItem(id: String) {
        localDb.delete(id)
    }

    /** Resolve in favour of the server: overwrite the local record with the server's current version. */
    fun overrideClientItemWithServer(id: String) {
        viewModelScope.launch {
            val serverItem = server.get(id)
            if (serverItem != null) {
                localDb.overrideFromServer(serverItem)
            } else {
                localDb.remove(id)
            }
        }
    }

    fun touchServerItem(id: String) {
        viewModelScope.launch { server.touchRemote(id) }
    }

    fun deleteServerItem(id: String) {
        viewModelScope.launch { server.deleteRemote(id) }
    }

    fun synchronize() {
        viewModelScope.launch { syncManager.synchronize() }
    }

    fun clearLocalDb() {
        localDb.clear()
    }

    fun reset() {
        viewModelScope.launch {
            syncManager.resetData()
            server.clear()
        }
    }

    fun setFailNextSync(fail: Boolean) {
        _failNextSync.value = fail
        server.failNextSync = fail
    }

    fun setShowDeleted(show: Boolean) {
        _showDeleted.value = show
    }

    fun setRefreshOnForeground(enabled: Boolean) {
        _refreshOnForeground.value = enabled
        applyRefreshEvents()
    }

    fun setRefreshOnInternetBack(enabled: Boolean) {
        _refreshOnInternetBack.value = enabled
        applyRefreshEvents()
    }

    private fun applyRefreshEvents() {
        SyncDemoRegistry.setRefreshEvents(
            onForeground = _refreshOnForeground.value,
            onInternetBack = _refreshOnInternetBack.value,
        )
    }
}

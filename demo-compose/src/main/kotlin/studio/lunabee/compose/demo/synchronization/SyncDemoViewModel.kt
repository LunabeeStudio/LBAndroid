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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.lunabee.synchronization.connectivity.LBConnectivityManager
import studio.lunabee.synchronization.syncmanager.LBSyncProcessStatus
import javax.inject.Inject
import kotlin.time.Duration

@HiltViewModel
class SyncDemoViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val registry: SyncDemoRegistry,
) : ViewModel() {

    private val server: FakeRemoteServer = registry.server
    private val localDb: LocalItemDatabase = registry.localDb
    private val syncManager: DemoItemSyncManager = registry.syncManager

    val localItems: StateFlow<List<LocalItem>> = localDb.items
    val serverItems: StateFlow<List<ServerItem>> = server.items
    val status: StateFlow<LBSyncProcessStatus> = syncManager.status

    /** The engine's automatic-retry delay for a failed run (null disables retry). */
    val retryTempo: Duration? = syncManager.retryTempo

    private val _retryCount: MutableStateFlow<Int> = MutableStateFlow(0)

    /** Number of consecutive failed sync attempts; reset to 0 on the next success. */
    val retryCount: StateFlow<Int> = _retryCount.asStateFlow()

    val isOnline: StateFlow<Boolean> = LBConnectivityManager.observeNetworkStates(context)
        .map { it.isConnected }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = LBConnectivityManager.getNetworkState(context).isConnected,
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
        // Count failed attempts: each (auto-retried) failure publishes a fresh *WithError.
        viewModelScope.launch {
            syncManager.status.collect { current ->
                when (current) {
                    is LBSyncProcessStatus.DownloadFinishWithError,
                    is LBSyncProcessStatus.UploadFinishWithError,
                    -> _retryCount.update { it + 1 }

                    is LBSyncProcessStatus.SyncSuccessfully -> _retryCount.value = 0

                    else -> Unit
                }
            }
        }
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
        registry.setRefreshEvents(
            onForeground = _refreshOnForeground.value,
            onInternetBack = _refreshOnInternetBack.value,
        )
    }
}

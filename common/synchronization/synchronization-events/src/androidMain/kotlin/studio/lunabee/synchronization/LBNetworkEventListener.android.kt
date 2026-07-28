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

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import studio.lunabee.synchronization.connectivity.LBConnectivityManager
import studio.lunabee.synchronization.connectivity.NetworkState
import studio.lunabee.synchronization.syncmanager.LBSyncRefreshEvent
import kotlin.reflect.KClass

actual class LBNetworkEventListener(
    private val context: Context,
) : LBSyncEventListener {
    private lateinit var lastNetworkState: NetworkState
    private var networkListenerJob: Job? = null
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    actual override fun register(
        onEvent: (eventType: KClass<out LBSyncRefreshEvent>) -> Unit,
    ) {
        val appContext = context.applicationContext
        lastNetworkState = LBConnectivityManager.getNetworkState(appContext)
        networkListenerJob?.cancel()
        networkListenerJob = scope.launch {
            LBConnectivityManager.observeNetworkStates(appContext).collect { networkState ->
                if (networkState.isConnected) {
                    networkLogger.v("Internet is available with transport ${networkState.connectionType}")
                    if (!lastNetworkState.isConnected) {
                        onEvent(LBSyncRefreshEvent.InternetIsBack::class)
                    }
                } else {
                    networkLogger.v("Internet is disabled")
                }
                lastNetworkState = networkState
            }
        }
    }
}

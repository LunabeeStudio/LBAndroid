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

package studio.lunabee.synchronization.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import studio.lunabee.synchronization.connectivity.LBConnectivityManager.getNetworkState
import studio.lunabee.synchronization.connectivity.LBConnectivityManager.observeNetworkStates

/**
 * Observe device connectivity through the [ConnectivityManager.NetworkCallback] +
 * [NetworkCapabilities] APIs. Exposes a cold [observeNetworkStates] [Flow] and a one-shot [getNetworkState]
 * snapshot.
 *
 * The `ACCESS_NETWORK_STATE` permission is declared in the library manifest and merges into the
 * consumer app.
 */
object LBConnectivityManager {

    /**
     * Snapshot of the current device network state.
     */
    fun getNetworkState(context: Context): NetworkState {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
            ?: return NetworkState(isConnected = false, connectionType = null)
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let(connectivityManager::getNetworkCapabilities)
        return capabilities.toNetworkState()
    }

    /**
     * Cold [Flow] emitting the [NetworkState] on every connectivity change (and once on collection with
     * the current state). Backed by a [ConnectivityManager.NetworkCallback] registered for the
     * INTERNET-capable default network and unregistered on cancellation. Consecutive duplicates are
     * dropped.
     */
    fun observeNetworkStates(context: Context): Flow<NetworkState> {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        return if (connectivityManager == null) {
            flowOf(NetworkState(isConnected = false, connectionType = null))
        } else {
            callbackFlow {
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        trySend(getNetworkState(context))
                    }

                    override fun onLost(network: Network) {
                        trySend(getNetworkState(context))
                    }

                    override fun onUnavailable() {
                        trySend(getNetworkState(context))
                    }

                    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                        trySend(getNetworkState(context))
                    }
                }

                trySend(getNetworkState(context))
                // Track the default network: registerDefaultNetworkCallback reliably fires onLost when the last
                // network drops (e.g. airplane mode), which a NetworkRequest-scoped callback may miss.
                connectivityManager.registerDefaultNetworkCallback(callback)
                awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
            }.distinctUntilChanged()
        }
    }

    private fun NetworkCapabilities?.toNetworkState(): NetworkState {
        if (this == null || !hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return NetworkState(isConnected = false, connectionType = null)
        }
        return NetworkState(isConnected = true, connectionType = LBNetworkTransport.from(this))
    }
}

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

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Observe device connectivity through the modern [ConnectivityManager.NetworkCallback] +
 * [NetworkCapabilities] APIs (the legacy `CONNECTIVITY_ACTION` broadcast and `activeNetworkInfo` are
 * deprecated). Exposes a cold [networkStates] [Flow] and a one-shot [getNetworkState] snapshot.
 *
 * **WARNING**: you need the `ACCESS_NETWORK_STATE` permission in your app manifest.
 */
object LBConnectivityManager {

    /**
     * Snapshot of the current device network state.
     *
     * **WARNING**: you need the `ACCESS_NETWORK_STATE` permission in your app manifest.
     */
    @SuppressLint("MissingPermission")
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
     *
     * **WARNING**: you need the `ACCESS_NETWORK_STATE` permission in your app manifest.
     */
    @SuppressLint("MissingPermission")
    fun networkStates(context: Context): Flow<NetworkState> = callbackFlow {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        if (connectivityManager == null) {
            trySend(NetworkState(isConnected = false, connectionType = null))
            awaitClose { }
            return@callbackFlow
        }

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

    /**
     * Map [NetworkCapabilities] to a [NetworkState]: connected when the network has the INTERNET
     * capability, with [NetworkState.connectionType] set to the primary [LBNetworkTransport].
     */
    private fun NetworkCapabilities?.toNetworkState(): NetworkState {
        if (this == null || !hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return NetworkState(isConnected = false, connectionType = null)
        }
        return NetworkState(isConnected = true, connectionType = LBNetworkTransport.from(this))
    }
}

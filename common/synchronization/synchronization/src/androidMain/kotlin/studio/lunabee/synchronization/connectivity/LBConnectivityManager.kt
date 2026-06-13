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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.net.ConnectivityManager

/**
 * Use this class to get notify when connection change and to get connection state/type
 * **WARNING** : You need to add ACCESS_NETWORK_STATE permission in your app manifest
 */
@Suppress("DEPRECATION")
class LBConnectivityManager {

    /**
     * The action you want to do when connection change is detected
     */
    var listener: BroadcastReceiver? = null

    /**
     * Start listening connection changes
     */
    fun startListening(context: Context) {
        listener?.let {
            context.registerReceiver(
                listener,
                IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION),
            )
        }
    }

    /**
     * Stop listening connection changes
     */
    fun stopListening(context: Context) {
        listener?.let(context::unregisterReceiver)
        listener = null
    }

    companion object {
        /**
         * Get the object NetworkState for the current device network
         * **WARNING** : You need to add ACCESS_NETWORK_STATE permission in your app manifest
         */
        @SuppressLint("MissingPermission")
        fun getNetworkState(context: Context): NetworkState {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetworkInfo
            return if (activeNetwork != null && activeNetwork.isConnectedOrConnecting) {
                NetworkState(true, activeNetwork.type)
            } else {
                NetworkState(false, null)
            }
        }
    }
}

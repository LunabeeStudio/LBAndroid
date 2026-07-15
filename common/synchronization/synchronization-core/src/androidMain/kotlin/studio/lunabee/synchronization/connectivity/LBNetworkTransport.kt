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

import android.net.NetworkCapabilities

/**
 * Transport backing the active network. The framework only ships bare `Int` constants
 * (`NetworkCapabilities.TRANSPORT_*`), so this enum gives them a type-safe, exhaustive shape.
 */
enum class LBNetworkTransport {
    Wifi,
    Cellular,
    Ethernet,
    Bluetooth,
    Vpn,
    ;

    companion object {
        /**
         * The primary [LBNetworkTransport] carried by [capabilities], or null when none of the mapped
         * transports is present.
         */
        fun from(capabilities: NetworkCapabilities): LBNetworkTransport? = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Wifi
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Cellular
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Ethernet
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> Bluetooth
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> Vpn
            else -> null
        }
    }
}

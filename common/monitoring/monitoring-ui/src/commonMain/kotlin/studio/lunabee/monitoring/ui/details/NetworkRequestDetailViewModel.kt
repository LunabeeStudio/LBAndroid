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

package studio.lunabee.monitoring.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import studio.lunabee.monitoring.core.LBMonitoring
import studio.lunabee.monitoring.core.LBRequest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

internal class NetworkRequestDetailViewModel(
    private val monitoring: LBMonitoring,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val params: NetworkRequestDetailDestination = savedStateHandle.toRoute()

    val request: StateFlow<LBRequest?> = monitoring
        .getRequestByIdAsFlow(requestId = params.requestId.let(Uuid::parseHex))
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(), initialValue = null)

    fun deleteRequest() {
        viewModelScope.launch {
            monitoring.removeRequestById(requestId = params.requestId.let(Uuid::parseHex))
        }
    }
}

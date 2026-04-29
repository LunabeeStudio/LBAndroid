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

package com.lunabee.demo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import platform.UIKit.UIViewController
import studio.lunabee.monitoring.core.LBMonitoring
import studio.lunabee.monitoring.room.LBRoomMonitoring
import studio.lunabee.monitoring.ui.LBMonitoringMainController

object DemoMonitoring {
    internal val delegate: LBMonitoring by lazy {
        LBRoomMonitoring.get()
    }
}

object DemoMonitoringControllerFactory {
    fun create(
        monitoring: DemoMonitoring,
        closeMonitoring: () -> Unit,
    ): UIViewController {
        return LBMonitoringMainController.get(
            monitoring = monitoring.delegate,
            closeMonitoring = closeMonitoring,
        )
    }
}

class DemoRemoteDatasource(
    monitoring: DemoMonitoring,
) {
    private val datasource = DemoRemoteDatasourceImpl(monitoring = monitoring.delegate)
    private val dogFactsFlow = MutableStateFlow("Click the button to display a new fact!")

    fun dogFacts(): Flow<String> {
        return dogFactsFlow
    }

    suspend fun refreshDogFact() {
        dogFactsFlow.value = datasource.getDogFact()
    }

    suspend fun refreshDogFact404() {
        dogFactsFlow.value = datasource.getDogFact404()
    }
}

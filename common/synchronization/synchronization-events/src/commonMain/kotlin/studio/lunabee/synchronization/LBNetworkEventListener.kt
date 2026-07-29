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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Job
import studio.lunabee.logger.LBLogger
import studio.lunabee.synchronization.syncmanager.LBSyncRefreshEvent
import studio.lunabee.synchronization.syncmanager.LBSyncRefreshEventData

expect class LBNetworkEventListener : LBSyncEventListener<LBSyncRefreshEventData.InternetIsBack> {

    /**
     * Call this to let the LBSyncOperator refresh the sync managers for network changes. The refresh is
     * performed when a reconnection is detected (new state is connected AND the previous state was not).
     *
     * **WARNING** : A sync manager can only be refreshed if its group carries
     * [LBSyncRefreshEvent.InternetIsBack].
     */
    override fun register(
        onEvent: suspend (data: LBSyncRefreshEventData.InternetIsBack) -> Unit,
    ): Job
}

internal val networkLogger: Logger = LBLogger.get("$LogTag Network")

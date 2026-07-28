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

import kotlinx.coroutines.flow.Flow
import studio.lunabee.synchronization.syncmanager.LBSyncRefreshEvent
import kotlin.reflect.KClass

expect class LBAppForegroundEventListener : LBSyncEventListener {

    /**
     * Call this to let the LBSyncOperator refresh the sync managers when the app enters the foreground.
     * Observes [ProcessLifecycleOwner]'s lifecycle as a [Flow] (no broadcasts): a foreground transition
     * triggers the refresh and starts the server-notification listeners; a background transition stops
     * them.
     *
     * **WARNING** : A sync manager can only be refreshed if its group carries
     * [LBSyncRefreshEvent.AppForeground].
     */
    override fun register(
        onEvent: (eventType: KClass<out LBSyncRefreshEvent>) -> Unit,
    )
}

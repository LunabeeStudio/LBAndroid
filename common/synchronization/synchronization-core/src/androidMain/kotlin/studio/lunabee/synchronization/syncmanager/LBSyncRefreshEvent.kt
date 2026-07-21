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

package studio.lunabee.synchronization.syncmanager

import studio.lunabee.synchronization.syncmanager.LBSyncRefreshEvent.AppForeground
import studio.lunabee.synchronization.syncmanager.LBSyncRefreshEvent.InternetIsBack
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Events that can trigger a sync manager refresh. [AppForeground] and [InternetIsBack] are handled by
 * `LBSyncOperator`.
 *
 * @param minimumDelay the minimum debounce duration that must elapse since the last successful sync
 * before this event triggers a refresh.
 */
sealed class LBSyncRefreshEvent(private val minimumDelay: Duration) {

    internal fun isDelayElapsed(lastSuccessfulSync: Instant): Boolean =
        lastSuccessfulSync + minimumDelay < Clock.System.now()

    class AppForeground(minimumDelay: Duration = Duration.ZERO) : LBSyncRefreshEvent(minimumDelay)

    class InternetIsBack(minimumDelay: Duration = Duration.ZERO) : LBSyncRefreshEvent(minimumDelay)
}

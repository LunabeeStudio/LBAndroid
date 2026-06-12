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
import java.util.Date

/**
 * There is the list of event than can triggered and sync manager refresh
 * [AppForeground] and [InternetIsBack] are managed in LBSyncOperator
 * You can use NOTIFICATION_FROM_SERVER in LiveQueries for example
 */

sealed class LBSyncRefreshEvent(private val minimumDelayMs: Long) {

    internal fun isDelayElapsed(lastSuccessfulSync: Date): Boolean =
        lastSuccessfulSync.time + minimumDelayMs < Date().time

    class AppForeground(minimumDelayMs: Long = 0L) : LBSyncRefreshEvent(minimumDelayMs)

    class InternetIsBack(minimumDelayMs: Long = 0L) : LBSyncRefreshEvent(minimumDelayMs)
}

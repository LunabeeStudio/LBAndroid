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

import java.util.UUID

/**
 * LBSyncToken is used to get notify of a status change for a specific sync manager
 */
@Suppress("unused")
class LBSyncToken(
    var syncManager: LBGenericSyncManager,
    var changeStatusClosure: LBSyncChangeStatusClosure,
    val id: String = UUID.randomUUID().toString(),
) {

    /**
     * remove it self from the sync manager -> stop listening
     */
    fun invalidate() {
        syncManager.removeObserver(this)
    }
}

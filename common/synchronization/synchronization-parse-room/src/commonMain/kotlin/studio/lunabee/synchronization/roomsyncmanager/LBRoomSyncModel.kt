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

package studio.lunabee.synchronization.roomsyncmanager

import kotlin.time.Instant

/**
 * Sync bookkeeping every Room entity synced by [LBRoomSyncManager] must expose.
 *
 * @property lbLocalId **must be the Room primary key**
 * @property lbServerId server object id, null until the row has been pushed
 * @property lbUpdatedAt server `updatedAt`, used for incremental cursoring
 * @property lbInSync false ⇒ pending upload
 * @property lbDeleted soft-delete flag — deleted rows stay in the table (hidden by app read queries)
 *  until the deletion has been pushed and the catalog re-synced
 */
interface LBRoomSyncModel {
    val lbLocalId: String
    val lbServerId: String?
    val lbUpdatedAt: Instant?
    val lbInSync: Boolean
    val lbDeleted: Boolean
}

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

package studio.lunabee.compose.demo.synchronization

import kotlin.time.Instant

data class ServerItem(
    val id: String,
    val label: String,
    val updatedAt: Instant,
    val isDeleted: Boolean = false,
)

/**
 * A record as it lives in the local client database. This is the `LocalData` of the sync manager.
 * [isSynced] is `false` while the item only exists locally and still needs to be pushed.
 *
 * [baseUpdatedAt] is the server `updatedAt` observed at the last successful sync (null for an item that
 * was never synced). Comparing it against the freshly fetched server `updatedAt` while the item is
 * dirty is how a both-sides edit is detected as a conflict.
 */
data class LocalItem(
    val id: String,
    val label: String,
    val updatedAt: Instant,
    val isSynced: Boolean,
    val baseUpdatedAt: Instant? = null,
    val isConflicted: Boolean = false,
    val isDeleted: Boolean = false,
) {
    fun toServerItem(): ServerItem = ServerItem(
        id = id,
        label = label,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
    )
}

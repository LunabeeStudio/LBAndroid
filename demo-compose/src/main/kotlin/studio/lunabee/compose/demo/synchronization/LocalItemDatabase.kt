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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * An in-memory stand-in for the client's local database (e.g. a Room database). A real app would back
 * this with `:synchronization-parse-room`; here a [MutableStateFlow] keeps the demo dependency-free.
 */
class LocalItemDatabase {

    private val _items: MutableStateFlow<List<LocalItem>> = MutableStateFlow(emptyList())

    val items: StateFlow<List<LocalItem>> = _items.asStateFlow()

    fun addLocal(label: String) {
        _items.update { current ->
            current + LocalItem(
                id = Uuid.random().toString(),
                label = label,
                updatedAt = Clock.System.now(),
                isSynced = false,
            )
        }
    }

    /**
     * Apply records coming from the server (tombstones included). Per item:
     *  - a server tombstone wins: the local copy is marked deleted & synced, so a delete on the server
     *    (or echoed back after a local delete was pushed) hides the item on this client too;
     *  - a record with no local copy, or whose local copy is clean, is accepted from the server;
     *  - a **dirty** local copy whose server counterpart moved off its [LocalItem.baseUpdatedAt] base is
     *    a both-sides edit: the local edit is kept and the item is flagged [LocalItem.isConflicted];
     *  - a dirty local copy the server has not touched is kept as-is for upload (only the base is
     *    advanced) — download must not clobber a pending local edit before it is pushed.
     *
     * Conflicts are isolated per item, so the rest of the sync proceeds normally.
     */
    fun applyServerChanges(serverItems: List<ServerItem>) {
        _items.update { current ->
            val byId = current.associateBy { it.id }.toMutableMap()
            serverItems.forEach { server ->
                val local = byId[server.id]
                byId[server.id] = when {
                    server.isDeleted -> LocalItem(
                        id = server.id,
                        label = server.label,
                        updatedAt = server.updatedAt,
                        isSynced = true,
                        baseUpdatedAt = server.updatedAt,
                        isDeleted = true,
                    )

                    local == null || local.isSynced -> LocalItem(
                        id = server.id,
                        label = server.label,
                        updatedAt = server.updatedAt,
                        isSynced = true,
                        baseUpdatedAt = server.updatedAt,
                    )

                    // The server echoes back exactly our pending edit: the push succeeded, now in sync.
                    server.updatedAt == local.updatedAt ->
                        local.copy(isSynced = true, isConflicted = false, baseUpdatedAt = server.updatedAt)

                    local.baseUpdatedAt != null && server.updatedAt != local.baseUpdatedAt ->
                        local.copy(isConflicted = true, baseUpdatedAt = server.updatedAt)

                    else -> local.copy(baseUpdatedAt = server.updatedAt)
                }
            }
            byId.values.sortedBy { it.updatedAt }
        }
    }

    /**
     * Bump a record's `updatedAt` to now and flag it dirty so the next sync pushes the change. Clears
     * [LocalItem.isConflicted], so touching a conflicted item resolves the conflict in favour of the
     * local edit (it will be pushed on the next sync).
     */
    fun touch(id: String) {
        _items.update { current ->
            current.map { item ->
                if (item.id == id) {
                    item.copy(updatedAt = Clock.System.now(), isSynced = false, isConflicted = false)
                } else {
                    item
                }
            }
        }
    }

    /**
     * Soft-delete a local record: flag it as a tombstone (kept, not removed) and mark it dirty so the
     * deletion is pushed to the server on the next sync.
     */
    fun delete(id: String) {
        _items.update { current ->
            current.map { item ->
                if (item.id == id) {
                    // Bump updatedAt so the tombstone is a real change to push, not mistaken for an echo.
                    item.copy(updatedAt = Clock.System.now(), isDeleted = true, isSynced = false)
                } else {
                    item
                }
            }
        }
    }

    /**
     * The records that need to be pushed: dirty and not awaiting manual conflict resolution (tombstones
     * included, so local deletes propagate). Conflicted items are held back so the sync never silently
     * overwrites the diverged server version.
     */
    fun unsynced(): List<LocalItem> = _items.value.filter { !it.isSynced && !it.isConflicted }

    /**
     * Force the local record to match the server [server], discarding any pending local edit or
     * conflict (the keep-server resolution).
     */
    fun overrideFromServer(server: ServerItem) {
        _items.update { current ->
            current.map { item ->
                if (item.id == server.id) {
                    LocalItem(
                        id = server.id,
                        label = server.label,
                        updatedAt = server.updatedAt,
                        isSynced = true,
                        baseUpdatedAt = server.updatedAt,
                        isDeleted = server.isDeleted,
                    )
                } else {
                    item
                }
            }
        }
    }

    /** Hard-remove a local record (the server has no counterpart to keep). */
    fun remove(id: String) {
        _items.update { current -> current.filterNot { it.id == id } }
    }

    fun clear() {
        _items.value = emptyList()
    }
}

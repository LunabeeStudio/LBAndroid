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

package studio.lunabee.synchronization.store

import kotlin.time.Instant

/**
 * Framework-agnostic persistence for a sync manager's cursors. The engine depends only on this
 * interface; a concrete backend (DataStore, Room, …) is provided by a separate module and installed via
 * [LBSyncStorage].
 *
 * Cursors are keyed by a manager's [SyncKey]; the key is persisted, so renaming a manager (and
 * therefore its [SyncKey]) loses the saved cursor. Two cursors are tracked per key:
 * - the last server-side `updatedAt` (incremental-sync cursor);
 * - the last successful local sync date.
 *
 * @see LBSyncStorage
 */
interface SyncTimestampLocalDataSource {

    /**
     * Reads the last server-side `updatedAt` cursor for [syncKey].
     *
     * @return the cursor, or `null` if no value has been stored yet.
     */
    suspend fun lastServerSyncDate(syncKey: SyncKey): Instant?

    /**
     * Reads the last successful local sync date for [syncKey].
     *
     * @return the date, or `null` if no value has been stored yet.
     */
    suspend fun lastSuccessfulSyncDate(syncKey: SyncKey): Instant?

    /**
     * Writes both cursors for [syncKey] in a single transaction. Each value is written only when it is
     * non-null; a `null` argument leaves the corresponding stored value unchanged.
     *
     * @param serverDate the server-side cursor to store, or `null` to leave it unchanged.
     * @param localDate the local sync date to store, or `null` to leave it unchanged.
     */
    suspend fun saveSyncDates(
        syncKey: SyncKey,
        serverDate: Instant?,
        localDate: Instant?,
    )

    /**
     * Removes both cursors for [syncKey], leaving any other key's cursors intact.
     */
    suspend fun clear(syncKey: SyncKey)

    /**
     * Wipes the whole store, removing every key's cursors.
     */
    suspend fun clearAll()
}

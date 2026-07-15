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

package studio.lunabee.synchronization.room

import studio.lunabee.synchronization.store.SyncKey
import studio.lunabee.synchronization.store.SyncTimestampStore
import kotlin.time.Instant

/**
 * [SyncTimestampStore] backed by a Room database. Obtain one through a platform factory
 * (`Context.roomSyncTimestampStore()` on Android, `roomSyncTimestampStore()` on iOS) rather than
 * constructing it directly.
 */
class RoomSyncTimestampStore internal constructor(private val database: SyncRoomDatabase) : SyncTimestampStore {

    override suspend fun lastServerSyncDate(syncKey: SyncKey): Instant? =
        database.syncTimestampDao().serverDate(syncKey)

    override suspend fun lastSuccessfulSyncDate(syncKey: SyncKey): Instant? =
        database.syncTimestampDao().localDate(syncKey)

    override suspend fun saveSyncDates(syncKey: SyncKey, serverDate: Instant?, localDate: Instant?) {
        database.syncTimestampDao().saveSyncDates(syncKey = syncKey, serverDate = serverDate, localDate = localDate)
    }

    override suspend fun clear(syncKey: SyncKey) {
        database.syncTimestampDao().delete(syncKey)
    }

    override suspend fun clearAll() {
        database.syncTimestampDao().clearAll()
    }
}

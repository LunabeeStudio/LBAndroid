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

package studio.lunabee.synchronization.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import studio.lunabee.synchronization.room.entity.SyncTimestampEntity
import studio.lunabee.synchronization.store.SyncKey
import kotlin.time.Instant

@Dao
internal interface SyncTimestampDao {

    @Query("SELECT serverDate FROM sync_timestamp WHERE syncKey = :syncKey")
    suspend fun getServerDate(syncKey: SyncKey): Instant?

    @Query("SELECT localDate FROM sync_timestamp WHERE syncKey = :syncKey")
    suspend fun getLocalDate(syncKey: SyncKey): Instant?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: SyncTimestampEntity)

    /**
     * Writes only the non-null cursors: a `null` argument leaves the stored column unchanged
     * (`COALESCE(:new, existing)`), matching the storage contract.
     */
    @Query(
        "UPDATE sync_timestamp SET " +
            "serverDate = COALESCE(:serverDate, serverDate), " +
            "localDate = COALESCE(:localDate, localDate) " +
            "WHERE syncKey = :syncKey",
    )
    suspend fun mergeDates(syncKey: SyncKey, serverDate: Instant?, localDate: Instant?)

    /**
     * Ensures a row exists for [syncKey] then merges the non-null cursors into it, in one transaction.
     */
    @Transaction
    suspend fun saveSyncDates(syncKey: SyncKey, serverDate: Instant?, localDate: Instant?) {
        insertIfAbsent(SyncTimestampEntity(syncKey = syncKey, serverDate = null, localDate = null))
        mergeDates(syncKey = syncKey, serverDate = serverDate, localDate = localDate)
    }

    @Query("DELETE FROM sync_timestamp WHERE syncKey = :syncKey")
    suspend fun delete(syncKey: SyncKey)

    @Query("DELETE FROM sync_timestamp")
    suspend fun clearAll()
}

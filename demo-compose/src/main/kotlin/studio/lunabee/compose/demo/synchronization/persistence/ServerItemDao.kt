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

package studio.lunabee.compose.demo.synchronization.persistence

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerItemDao {
    @Query("SELECT * FROM server_item ORDER BY updatedAt")
    fun observeAll(): Flow<List<ServerItemEntity>>

    /** Every row, including tombstones, fed to the sync engine so deletes propagate to clients. */
    @Query("SELECT * FROM server_item ORDER BY updatedAt")
    suspend fun getAll(): List<ServerItemEntity>

    @Query("SELECT * FROM server_item WHERE id = :id")
    suspend fun getById(id: String): ServerItemEntity?

    @Upsert
    suspend fun upsert(items: List<ServerItemEntity>)

    @Query("UPDATE server_item SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    /** Soft-delete: flag the row as a tombstone instead of removing it, so it can still be shown. */
    @Query("UPDATE server_item SET isDeleted = 1 WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM server_item")
    suspend fun clear()
}

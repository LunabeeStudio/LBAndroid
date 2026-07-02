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

import androidx.room.Upsert

/**
 * Generic persistence base for a synced Room entity. The typed [upsert] is resolved generically by
 * Room codegen at the concrete `@Dao` subclass (BaseDao pattern: `@Dao abstract class FooDao :
 * LBRoomSyncDao<Foo>()`).
 *
 * [notInSync] / [markInSync] / [deleteAll] **must be overridden** by each subclass with a
 * table-literal `@Query`: Room `@Query` can't take a table name as a runtime param, and `@RawQuery`
 * is read-only (can't express the writes) and has no row mapper for a generic `List<T>`.
 *
 * [notInSync] returns dirty rows **including** soft-deleted ones (a pending deletion must still be
 * pushed); the `WHERE lbDeleted = 0` filtering is the subclass's app-facing read queries, not here.
 */
abstract class LBRoomSyncDao<T : LBRoomSyncModel> {

    @Upsert
    abstract suspend fun upsert(rows: List<T>)

    abstract suspend fun notInSync(): List<T>

    abstract suspend fun markInSync(localIds: List<String>)

    abstract suspend fun deleteAll()
}

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

package studio.lunabee.synchronization.parsektorroom.local

import studio.lunabee.synchronization.remote.LBSyncLocalDataSource
import studio.lunabee.synchronization.roomsyncmanager.LBRoomSyncDao
import studio.lunabee.synchronization.roomsyncmanager.LBRoomSyncModel

/**
 * [LBSyncLocalDataSource] on an [LBRoomSyncDao].
 */
internal class LBRoomSyncLocalDataSource<R : LBRoomSyncModel>(
    private val dao: LBRoomSyncDao<R>,
) : LBSyncLocalDataSource<R> {
    /** Upserts [objects] except the rows still to push, so a pull never overwrites a local edit. */
    override suspend fun savePulled(objects: List<R>) {
        val pending = dao.notInSync().mapTo(mutableSetOf()) { it.lbLocalId }
        val rows = objects.filterNot { it.lbLocalId in pending }
        if (rows.isNotEmpty()) dao.upsert(rows)
    }

    override suspend fun objectsToPush(): List<R> = dao.notInSync()

    /**
     * Marks the row in sync unconditionally. Known gap: an edit made while the row was uploading is marked in sync
     * too, and is pushed with the next edit of the row.
     */
    override suspend fun markPushed(pushed: R) {
        dao.markInSync(listOf(pushed.lbLocalId))
    }

    override suspend fun clear() {
        dao.deleteAll()
    }
}

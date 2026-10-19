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

package studio.lunabee.synchronization.parsektorroom

import studio.lunabee.synchronization.roomsyncmanager.LBRoomSyncDao

internal class FakeRoomDao(rows: List<TestRow> = emptyList()) : LBRoomSyncDao<TestRow>() {
    val rows: MutableList<TestRow> = rows.toMutableList()
    val upserts: MutableList<List<TestRow>> = mutableListOf()
    var deleteAllCalls: Int = 0

    override suspend fun upsert(rows: List<TestRow>) {
        upserts += rows
        rows.forEach { row ->
            this.rows.removeAll { it.lbLocalId == row.lbLocalId }
            this.rows += row
        }
    }

    override suspend fun notInSync(): List<TestRow> = rows.filterNot { it.lbInSync }

    override suspend fun markInSync(localIds: List<String>) {
        rows.indices.forEach { index ->
            if (rows[index].lbLocalId in localIds) rows[index] = rows[index].copy(lbInSync = true)
        }
    }

    override suspend fun deleteAll() {
        deleteAllCalls++
        rows.clear()
    }
}

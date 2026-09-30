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

package studio.lunabee.synchronization.remote

import kotlin.time.Instant

/**
 * In-memory live backend paging its records by keyset: ascending `(updatedAt, id)`, each page resuming after the
 * position encoded in the cursor. [onPageServed] runs after each page is read, to change the records mid-download.
 */
internal class FakeKeysetRemoteDataSource(
    private val pageSize: Int,
) : LBPullRemoteDataSource<Item> {
    private val records: MutableMap<String, Record> = mutableMapOf()
    var onPageServed: (() -> Unit)? = null

    fun put(item: Item, updatedAt: Instant) {
        records[item.id] = Record(item = item, position = Position(updatedAt = updatedAt, id = item.id))
    }

    override suspend fun fetchPage(cursor: String?, updatedAfter: Instant?): LBRemotePage<Item> {
        val after = cursor?.let(Position::decode)
        val page = records.values
            .filter { record -> updatedAfter == null || record.position.updatedAt > updatedAfter }
            .filter { record -> after == null || record.position > after }
            .sortedBy(Record::position)
            .take(pageSize)
        onPageServed?.invoke()
        return LBRemotePage(
            objects = page.map(Record::item),
            maxUpdatedAt = page.maxOfOrNull { record -> record.position.updatedAt },
            nextCursor = page.lastOrNull()?.position?.encode()?.takeIf { page.size == pageSize },
        )
    }

    private data class Record(val item: Item, val position: Position)

    private data class Position(val updatedAt: Instant, val id: String) : Comparable<Position> {
        override fun compareTo(other: Position): Int = compareValuesBy(this, other, Position::updatedAt, Position::id)

        fun encode(): String = "${updatedAt.toEpochMilliseconds()}:$id"

        companion object {
            fun decode(cursor: String): Position {
                val (millis, id) = cursor.split(":", limit = 2)
                return Position(updatedAt = Instant.fromEpochMilliseconds(millis.toLong()), id = id)
            }
        }
    }
}

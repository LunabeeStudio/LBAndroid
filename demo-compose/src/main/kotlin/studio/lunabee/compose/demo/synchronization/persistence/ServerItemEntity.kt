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

import androidx.room.Entity
import androidx.room.PrimaryKey
import studio.lunabee.compose.demo.synchronization.ServerItem
import kotlin.time.Instant

/**
 * Room row backing the fake remote server. `updatedAt` is stored as epoch millis to avoid a type
 * converter.
 */
@Entity(tableName = "server_item")
data class ServerItemEntity(
    @PrimaryKey
    val id: String,
    val label: String,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
) {
    fun toServerItem(): ServerItem = ServerItem(
        id = id,
        label = label,
        updatedAt = Instant.fromEpochMilliseconds(updatedAt),
        isDeleted = isDeleted,
    )

    companion object {
        fun fromServerItem(item: ServerItem): ServerItemEntity = ServerItemEntity(
            id = item.id,
            label = item.label,
            updatedAt = item.updatedAt.toEpochMilliseconds(),
            isDeleted = item.isDeleted,
        )
    }
}

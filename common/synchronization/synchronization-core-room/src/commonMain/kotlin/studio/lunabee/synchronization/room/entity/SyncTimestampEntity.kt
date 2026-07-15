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

package studio.lunabee.synchronization.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import studio.lunabee.synchronization.store.SyncKey
import kotlin.time.Instant

/**
 * One sync manager's cursor pair, keyed by its [SyncKey]. A `null` column means the corresponding cursor
 * has never been stored. The [SyncKey] value class stores natively (TEXT); the [Instant] dates go through
 * `InstantConverter`.
 */
@Entity(tableName = "sync_timestamp")
internal data class SyncTimestampEntity(
    @PrimaryKey val syncKey: SyncKey,
    val serverDate: Instant?,
    val localDate: Instant?,
)

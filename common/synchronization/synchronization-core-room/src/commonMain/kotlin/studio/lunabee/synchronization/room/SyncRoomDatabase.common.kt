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

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.SQLiteDriver
import kotlinx.coroutines.CoroutineDispatcher
import studio.lunabee.synchronization.room.dao.SyncTimestampDao
import studio.lunabee.synchronization.room.entity.SyncTimestampEntity

@Database(
    entities = [
        SyncTimestampEntity::class,
    ],
    version = 1,
)
@TypeConverters(InstantConverter::class)
@ConstructedBy(SyncRoomDatabaseConstructor::class)
abstract class SyncRoomDatabase : RoomDatabase() {
    internal abstract fun syncTimestampDao(): SyncTimestampDao
}

internal expect class RoomPlatformBuilder {
    fun builder(): RoomDatabase.Builder<SyncRoomDatabase>
}

/**
 * Automatically generated actual implementation.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object SyncRoomDatabaseConstructor : RoomDatabaseConstructor<SyncRoomDatabase> {
    // Needed as compilation error might randomly occur (i.e with Dokka for example).
    override fun initialize(): SyncRoomDatabase
}

internal const val DatabaseName: String = "studio.lunabee.synchronization.db"

internal fun getRoomDb(
    builder: RoomPlatformBuilder,
    driver: SQLiteDriver,
    dispatcher: CoroutineDispatcher?,
): SyncRoomDatabase {
    return builder
        .builder()
        .setDriver(driver)
        .apply { dispatcher?.let { setQueryCoroutineContext(it) } }
        .build()
}

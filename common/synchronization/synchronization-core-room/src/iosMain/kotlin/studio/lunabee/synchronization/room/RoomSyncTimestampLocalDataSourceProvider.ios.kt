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

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CoroutineDispatcher
import studio.lunabee.synchronization.store.SyncTimestampLocalDataSource

/**
 * Returns a [SyncTimestampLocalDataSource] backed by a Room database stored in the app's documents directory. Call
 * it once and install the result at startup; opening the same database file more than once is the
 * caller's responsibility to avoid.
 *
 * ```kotlin
 * LBSyncStorage.install(roomSyncTimestampLocalDataSource())
 * ```
 *
 * @param driver the SQLite driver Room runs on. Defaults to [BundledSQLiteDriver] (ships its own SQLite,
 * consistent across OS versions); pass e.g. `NativeSQLiteDriver()` to use the platform's SQLite instead.
 * @param dispatcher the coroutine context Room runs its queries on, or `null` (default) to keep Room's
 * own default query context.
 */
fun roomSyncTimestampLocalDataSource(
    driver: SQLiteDriver = BundledSQLiteDriver(),
    dispatcher: CoroutineDispatcher? = null,
): SyncTimestampLocalDataSource {
    val database = getRoomDb(builder = RoomPlatformBuilder(), driver = driver, dispatcher = dispatcher)
    return RoomSyncTimestampLocalDataSource(database = database)
}

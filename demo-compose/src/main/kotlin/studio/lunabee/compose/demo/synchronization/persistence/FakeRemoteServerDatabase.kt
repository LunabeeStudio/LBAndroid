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

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ServerItemEntity::class],
    version = 1,
)
abstract class FakeRemoteServerDatabase : RoomDatabase() {
    abstract fun serverItemDao(): ServerItemDao

    internal companion object {
        const val DatabaseName: String = "fake_remote_server.db"
    }
}

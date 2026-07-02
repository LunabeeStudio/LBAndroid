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

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database standing in for the remote backend's storage. Persisting it on disk lets the server
 * state survive process death, which is what a later background-sync demo needs.
 */
@Database(
    entities = [ServerItemEntity::class],
    version = 1,
)
abstract class FakeRemoteServerDatabase : RoomDatabase() {
    abstract fun serverItemDao(): ServerItemDao

    companion object {
        private const val DatabaseName: String = "fake_remote_server.db"

        @Volatile
        private var instance: FakeRemoteServerDatabase? = null

        fun getInstance(context: Context): FakeRemoteServerDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context = context.applicationContext,
                klass = FakeRemoteServerDatabase::class.java,
                name = DatabaseName,
            ).build().also { instance = it }
        }
    }
}

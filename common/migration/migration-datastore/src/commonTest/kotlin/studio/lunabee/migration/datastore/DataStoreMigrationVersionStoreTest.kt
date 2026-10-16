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

package studio.lunabee.migration.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class DataStoreMigrationVersionStoreTest {

    private fun freshDataStore(): DataStore<Preferences> {
        val path: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "migration_version_${Random.nextLong()}.preferences_pb"
        return PreferenceDataStoreFactory.createWithPath { path }
    }

    @Test
    fun absent_key_returns_default_version_test() = runTest {
        val store = DataStoreMigrationVersionStore(dataStore = freshDataStore())

        assertEquals(expected = 0, actual = store.getVersion())
    }

    @Test
    fun absent_key_returns_custom_version_test() = runTest {
        val store = DataStoreMigrationVersionStore(dataStore = freshDataStore(), versionWhenAbsent = 7)

        assertEquals(expected = 7, actual = store.getVersion())
    }

    @Test
    fun set_then_get_returns_saved_version_test() = runTest {
        val store = DataStoreMigrationVersionStore(dataStore = freshDataStore())

        store.setVersion(3)

        assertEquals(expected = 3, actual = store.getVersion())
    }

    @Test
    fun different_keys_keep_separate_versions_test() = runTest {
        val dataStore = freshDataStore()
        val first = DataStoreMigrationVersionStore(dataStore = dataStore, key = "first")
        val second = DataStoreMigrationVersionStore(dataStore = dataStore, key = "second")

        first.setVersion(2)
        second.setVersion(5)

        assertEquals(expected = 2, actual = first.getVersion())
        assertEquals(expected = 5, actual = second.getVersion())
    }
}

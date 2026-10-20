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
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first
import studio.lunabee.migration.LBMigrationVersionStore

/**
 * [LBMigrationVersionStore] saving the version in a [DataStore].
 *
 * @param dataStore store owned by the app
 * @param key preference key holding the version, use distinct keys to keep several versions in one [dataStore]
 * @param versionWhenAbsent version returned while nothing was saved under [key]
 */
class DataStoreMigrationVersionStore(
    private val dataStore: DataStore<Preferences>,
    key: String = "lbMigrationVersion",
    private val versionWhenAbsent: Int = 0,
) : LBMigrationVersionStore {
    private val versionKey: Preferences.Key<Int> = intPreferencesKey(key)

    override suspend fun getVersion(): Int = dataStore.data.first()[versionKey] ?: versionWhenAbsent

    override suspend fun setVersion(version: Int) {
        dataStore.edit { it[versionKey] = version }
    }
}

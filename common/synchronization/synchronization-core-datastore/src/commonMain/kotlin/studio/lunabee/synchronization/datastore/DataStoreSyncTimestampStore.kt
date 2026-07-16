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

package studio.lunabee.synchronization.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import studio.lunabee.synchronization.store.SyncKey
import studio.lunabee.synchronization.store.SyncTimestampStore
import kotlin.time.Instant

/**
 * [SyncTimestampStore] backed by an AndroidX DataStore [Preferences] store.
 *
 * Cursors are exposed as [Instant] but persisted as epoch-millis via [longPreferencesKey], converting at
 * the boundary. The key scheme is a persisted compatibility contract (existing installs keep their
 * cursors), so renaming a manager (and therefore its [SyncKey]) loses the saved cursor:
 * - server-date key = `"${syncKey.value}lastSyncDate"`
 * - local-date key  = `"${syncKey.value}lastSyncDate_localDate"`
 *
 * @param dataStore the preferences store all cursors are read from and written to.
 */
class DataStoreSyncTimestampStore(private val dataStore: DataStore<Preferences>) : SyncTimestampStore {

    override suspend fun lastServerSyncDate(syncKey: SyncKey): Instant? =
        dataStore.data.first()[serverDateKey(syncKey)]?.let { Instant.fromEpochMilliseconds(it) }

    override suspend fun lastSuccessfulSyncDate(syncKey: SyncKey): Instant? =
        dataStore.data.first()[localDateKey(syncKey)]?.let { Instant.fromEpochMilliseconds(it) }

    override suspend fun saveSyncDates(
        syncKey: SyncKey,
        serverDate: Instant?,
        localDate: Instant?,
    ) {
        dataStore.edit { preferences ->
            serverDate?.let { preferences[serverDateKey(syncKey)] = it.toEpochMilliseconds() }
            localDate?.let { preferences[localDateKey(syncKey)] = it.toEpochMilliseconds() }
        }
    }

    override suspend fun clear(syncKey: SyncKey) {
        dataStore.edit { preferences ->
            preferences.remove(serverDateKey(syncKey))
            preferences.remove(localDateKey(syncKey))
        }
    }

    override suspend fun clearAll() {
        dataStore.edit { preferences -> preferences.clear() }
    }

    private fun serverDateKey(syncKey: SyncKey) = longPreferencesKey("${syncKey.value}lastSyncDate")

    private fun localDateKey(syncKey: SyncKey) = longPreferencesKey("${syncKey.value}lastSyncDate_localDate")
}

/**
 * Base file name of the process-wide sync cursor store. Matches the legacy SharedPreferences file name
 * so existing installs keep the same on-disk location.
 */
const val SyncDataStoreName: String = "com.lunabee.lbsynchronization"

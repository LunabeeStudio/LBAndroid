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

package studio.lunabee.synchronization.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Persists the sync cursors for a sync manager, backed by an AndroidX DataStore [Preferences] store.
 *
 * This is the extracted, multiplatform deep module for sync-cursor persistence. It speaks epoch
 * milliseconds only and preserves the legacy key scheme used by the previous SharedPreferences-based
 * engine, so renaming a manager (and therefore its [syncKey]) still loses the saved cursor exactly as
 * before:
 * - server-date key = `"${syncKey}lastSyncDate"`
 * - local-date key  = `"${syncKey}lastSyncDate_localDate"`
 *
 * @param dataStore the preferences store all cursors are read from and written to.
 */
public class SyncTimestampStore(private val dataStore: DataStore<Preferences>) {

    /**
     * Reads the last server-side `updatedAt` cursor for [syncKey].
     *
     * @return the cursor as epoch milliseconds, or `null` if no value has been stored yet.
     */
    public suspend fun lastServerSyncDate(syncKey: String): Long? =
        dataStore.data.first()[serverDateKey(syncKey)]

    /**
     * Reads the last successful local sync date for [syncKey].
     *
     * @return the date as epoch milliseconds, or `null` if no value has been stored yet.
     */
    public suspend fun lastSuccessfulSyncDate(syncKey: String): Long? =
        dataStore.data.first()[localDateKey(syncKey)]

    /**
     * Writes both cursors for [syncKey] in a single transaction. Each value is written only when it is
     * non-null; a `null` argument leaves the corresponding stored value unchanged.
     *
     * @param serverDateMillis the server-side cursor to store, or `null` to leave it unchanged.
     * @param localDateMillis the local sync date to store, or `null` to leave it unchanged.
     */
    public suspend fun saveSyncDates(
        syncKey: String,
        serverDateMillis: Long?,
        localDateMillis: Long?,
    ) {
        dataStore.edit { preferences ->
            serverDateMillis?.let { preferences[serverDateKey(syncKey)] = it }
            localDateMillis?.let { preferences[localDateKey(syncKey)] = it }
        }
    }

    /**
     * Removes both cursors for [syncKey], leaving any other key's cursors intact.
     */
    public suspend fun clear(syncKey: String) {
        dataStore.edit { preferences ->
            preferences.remove(serverDateKey(syncKey))
            preferences.remove(localDateKey(syncKey))
        }
    }

    /**
     * Wipes the whole store, removing every key's cursors.
     */
    public suspend fun clearAll() {
        dataStore.edit { preferences -> preferences.clear() }
    }

    private fun serverDateKey(syncKey: String) = longPreferencesKey("${syncKey}lastSyncDate")

    private fun localDateKey(syncKey: String) = longPreferencesKey("${syncKey}lastSyncDate_localDate")
}

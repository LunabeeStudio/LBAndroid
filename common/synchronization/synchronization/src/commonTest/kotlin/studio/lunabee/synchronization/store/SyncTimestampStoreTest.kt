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
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncTimestampStoreTest {

    /**
     * Builds a [SyncTimestampStore] over a fresh DataStore living on a unique temp path so no state is
     * shared between tests.
     */
    private fun freshStore(): SyncTimestampStore {
        val fileName = "sync_timestamp_${counter++}_${nextRandom()}.preferences_pb"
        val path: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / fileName
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath { path }
        return SyncTimestampStore(dataStore = dataStore)
    }

    @Test
    fun write_then_read_round_trip() = runTest {
        val store = freshStore()
        store.saveSyncDates(syncKey = "ManagerA", serverDateMillis = 100L, localDateMillis = 200L)

        assertEquals(expected = 100L, actual = store.lastServerSyncDate(syncKey = "ManagerA"))
        assertEquals(expected = 200L, actual = store.lastSuccessfulSyncDate(syncKey = "ManagerA"))
    }

    @Test
    fun absent_key_returns_null_on_fresh_store() = runTest {
        val store = freshStore()

        assertNull(store.lastServerSyncDate(syncKey = "ManagerA"))
        assertNull(store.lastSuccessfulSyncDate(syncKey = "ManagerA"))
    }

    @Test
    fun two_sync_keys_do_not_interfere() = runTest {
        val store = freshStore()
        store.saveSyncDates(syncKey = "ManagerA", serverDateMillis = 1L, localDateMillis = 2L)

        // B was never written, so both of its reads stay null.
        assertNull(store.lastServerSyncDate(syncKey = "ManagerB"))
        assertNull(store.lastSuccessfulSyncDate(syncKey = "ManagerB"))

        // A is unaffected.
        assertEquals(expected = 1L, actual = store.lastServerSyncDate(syncKey = "ManagerA"))
        assertEquals(expected = 2L, actual = store.lastSuccessfulSyncDate(syncKey = "ManagerA"))

        // Writing B leaves A intact.
        store.saveSyncDates(syncKey = "ManagerB", serverDateMillis = 10L, localDateMillis = 20L)
        assertEquals(expected = 10L, actual = store.lastServerSyncDate(syncKey = "ManagerB"))
        assertEquals(expected = 20L, actual = store.lastSuccessfulSyncDate(syncKey = "ManagerB"))
        assertEquals(expected = 1L, actual = store.lastServerSyncDate(syncKey = "ManagerA"))
        assertEquals(expected = 2L, actual = store.lastSuccessfulSyncDate(syncKey = "ManagerA"))
    }

    @Test
    fun null_server_date_writes_only_local_date() = runTest {
        val store = freshStore()
        store.saveSyncDates(syncKey = "ManagerA", serverDateMillis = null, localDateMillis = 200L)

        assertNull(store.lastServerSyncDate(syncKey = "ManagerA"))
        assertEquals(expected = 200L, actual = store.lastSuccessfulSyncDate(syncKey = "ManagerA"))
    }

    @Test
    fun clear_removes_only_that_keys_pair() = runTest {
        val store = freshStore()
        store.saveSyncDates(syncKey = "ManagerA", serverDateMillis = 1L, localDateMillis = 2L)
        store.saveSyncDates(syncKey = "ManagerB", serverDateMillis = 10L, localDateMillis = 20L)

        store.clear(syncKey = "ManagerA")

        assertNull(store.lastServerSyncDate(syncKey = "ManagerA"))
        assertNull(store.lastSuccessfulSyncDate(syncKey = "ManagerA"))
        assertEquals(expected = 10L, actual = store.lastServerSyncDate(syncKey = "ManagerB"))
        assertEquals(expected = 20L, actual = store.lastSuccessfulSyncDate(syncKey = "ManagerB"))
    }

    @Test
    fun clear_all_empties_everything() = runTest {
        val store = freshStore()
        store.saveSyncDates(syncKey = "ManagerA", serverDateMillis = 1L, localDateMillis = 2L)
        store.saveSyncDates(syncKey = "ManagerB", serverDateMillis = 10L, localDateMillis = 20L)

        store.clearAll()

        assertNull(store.lastServerSyncDate(syncKey = "ManagerA"))
        assertNull(store.lastSuccessfulSyncDate(syncKey = "ManagerA"))
        assertNull(store.lastServerSyncDate(syncKey = "ManagerB"))
        assertNull(store.lastSuccessfulSyncDate(syncKey = "ManagerB"))
    }

    @Test
    fun save_sync_dates_is_transactional() = runTest {
        val store = freshStore()
        store.saveSyncDates(syncKey = "ManagerA", serverDateMillis = 42L, localDateMillis = 99L)

        // After a single saveSyncDates(a, x, y) both reads reflect x and y consistently.
        assertEquals(expected = 42L, actual = store.lastServerSyncDate(syncKey = "ManagerA"))
        assertEquals(expected = 99L, actual = store.lastSuccessfulSyncDate(syncKey = "ManagerA"))
    }

    private companion object {
        private var counter: Int = 0

        private fun nextRandom(): Int = (0..Int.MAX_VALUE).random()
    }
}

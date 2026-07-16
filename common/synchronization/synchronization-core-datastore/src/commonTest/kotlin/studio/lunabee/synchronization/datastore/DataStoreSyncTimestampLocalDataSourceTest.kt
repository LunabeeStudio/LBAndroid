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
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import studio.lunabee.synchronization.store.SyncKey
import studio.lunabee.synchronization.store.SyncTimestampLocalDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class DataStoreSyncTimestampLocalDataSourceTest {

    private fun freshStore(): SyncTimestampLocalDataSource {
        val fileName = "sync_timestamp_${counter++}_${nextRandom()}.preferences_pb"
        val path: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / fileName
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath { path }
        return DataStoreSyncTimestampLocalDataSource(dataStore = dataStore)
    }

    private fun ms(value: Long): Instant = Instant.fromEpochMilliseconds(value)

    @Test
    fun write_then_read_round_trip() = runTest {
        val store = freshStore()
        store.saveSyncDates(syncKey = SyncKey("ManagerA"), serverDate = ms(100L), localDate = ms(200L))

        assertEquals(expected = ms(100L), actual = store.lastServerSyncDate(syncKey = SyncKey("ManagerA")))
        assertEquals(expected = ms(200L), actual = store.lastSuccessfulSyncDate(syncKey = SyncKey("ManagerA")))
    }

    @Test
    fun absent_key_returns_null_on_fresh_store() = runTest {
        val store = freshStore()

        assertNull(store.lastServerSyncDate(syncKey = SyncKey("ManagerA")))
        assertNull(store.lastSuccessfulSyncDate(syncKey = SyncKey("ManagerA")))
    }

    @Test
    fun two_sync_keys_do_not_interfere() = runTest {
        val store = freshStore()
        store.saveSyncDates(syncKey = SyncKey("ManagerA"), serverDate = ms(1L), localDate = ms(2L))

        assertNull(store.lastServerSyncDate(syncKey = SyncKey("ManagerB")))
        assertNull(store.lastSuccessfulSyncDate(syncKey = SyncKey("ManagerB")))

        assertEquals(expected = ms(1L), actual = store.lastServerSyncDate(syncKey = SyncKey("ManagerA")))
        assertEquals(expected = ms(2L), actual = store.lastSuccessfulSyncDate(syncKey = SyncKey("ManagerA")))

        store.saveSyncDates(syncKey = SyncKey("ManagerB"), serverDate = ms(10L), localDate = ms(20L))
        assertEquals(expected = ms(10L), actual = store.lastServerSyncDate(syncKey = SyncKey("ManagerB")))
        assertEquals(expected = ms(20L), actual = store.lastSuccessfulSyncDate(syncKey = SyncKey("ManagerB")))
        assertEquals(expected = ms(1L), actual = store.lastServerSyncDate(syncKey = SyncKey("ManagerA")))
        assertEquals(expected = ms(2L), actual = store.lastSuccessfulSyncDate(syncKey = SyncKey("ManagerA")))
    }

    @Test
    fun null_server_date_writes_only_local_date() = runTest {
        val store = freshStore()
        store.saveSyncDates(syncKey = SyncKey("ManagerA"), serverDate = null, localDate = ms(200L))

        assertNull(store.lastServerSyncDate(syncKey = SyncKey("ManagerA")))
        assertEquals(expected = ms(200L), actual = store.lastSuccessfulSyncDate(syncKey = SyncKey("ManagerA")))
    }

    @Test
    fun clear_removes_only_that_keys_pair() = runTest {
        val store = freshStore()
        store.saveSyncDates(syncKey = SyncKey("ManagerA"), serverDate = ms(1L), localDate = ms(2L))
        store.saveSyncDates(syncKey = SyncKey("ManagerB"), serverDate = ms(10L), localDate = ms(20L))

        store.clear(syncKey = SyncKey("ManagerA"))

        assertNull(store.lastServerSyncDate(syncKey = SyncKey("ManagerA")))
        assertNull(store.lastSuccessfulSyncDate(syncKey = SyncKey("ManagerA")))
        assertEquals(expected = ms(10L), actual = store.lastServerSyncDate(syncKey = SyncKey("ManagerB")))
        assertEquals(expected = ms(20L), actual = store.lastSuccessfulSyncDate(syncKey = SyncKey("ManagerB")))
    }

    @Test
    fun clear_all_empties_everything() = runTest {
        val store = freshStore()
        store.saveSyncDates(syncKey = SyncKey("ManagerA"), serverDate = ms(1L), localDate = ms(2L))
        store.saveSyncDates(syncKey = SyncKey("ManagerB"), serverDate = ms(10L), localDate = ms(20L))

        store.clearAll()

        assertNull(store.lastServerSyncDate(syncKey = SyncKey("ManagerA")))
        assertNull(store.lastSuccessfulSyncDate(syncKey = SyncKey("ManagerA")))
        assertNull(store.lastServerSyncDate(syncKey = SyncKey("ManagerB")))
        assertNull(store.lastSuccessfulSyncDate(syncKey = SyncKey("ManagerB")))
    }

    @Test
    fun save_sync_dates_is_transactional() = runTest {
        val store = freshStore()
        store.saveSyncDates(syncKey = SyncKey("ManagerA"), serverDate = ms(42L), localDate = ms(99L))

        assertEquals(expected = ms(42L), actual = store.lastServerSyncDate(syncKey = SyncKey("ManagerA")))
        assertEquals(expected = ms(99L), actual = store.lastSuccessfulSyncDate(syncKey = SyncKey("ManagerA")))
    }

    private companion object {
        private var counter: Int = 0

        private fun nextRandom(): Int = (0..Int.MAX_VALUE).random()
    }
}

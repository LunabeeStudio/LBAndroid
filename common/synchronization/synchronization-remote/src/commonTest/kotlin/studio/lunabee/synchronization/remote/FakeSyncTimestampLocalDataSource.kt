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

package studio.lunabee.synchronization.remote

import studio.lunabee.synchronization.store.LBSyncStorage
import studio.lunabee.synchronization.store.SyncKey
import studio.lunabee.synchronization.store.SyncTimestampLocalDataSource
import kotlin.time.Instant

/**
 * In-memory cursor store, installed as the process-wide backend on creation.
 */
internal class FakeSyncTimestampLocalDataSource : SyncTimestampLocalDataSource {
    private val serverDates: MutableMap<SyncKey, Instant> = mutableMapOf()
    private val localDates: MutableMap<SyncKey, Instant> = mutableMapOf()

    init {
        LBSyncStorage.install(store = this)
    }

    override suspend fun lastServerSyncDate(syncKey: SyncKey): Instant? = serverDates[syncKey]

    override suspend fun lastSuccessfulSyncDate(syncKey: SyncKey): Instant? = localDates[syncKey]

    override suspend fun saveSyncDates(syncKey: SyncKey, serverDate: Instant?, localDate: Instant?) {
        serverDate?.let { serverDates[syncKey] = it }
        localDate?.let { localDates[syncKey] = it }
    }

    override suspend fun clear(syncKey: SyncKey) {
        serverDates.remove(syncKey)
        localDates.remove(syncKey)
    }

    override suspend fun clearAll() {
        serverDates.clear()
        localDates.clear()
    }
}

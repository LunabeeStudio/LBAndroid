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

package studio.lunabee.compose.demo.synchronization

import studio.lunabee.synchronization.store.SyncKey
import studio.lunabee.synchronization.syncmanager.FetchPage
import studio.lunabee.synchronization.syncmanager.LBDefaultSyncManager
import kotlin.time.Instant

/**
 * Wires the demo's [LocalItemDatabase] and [FakeRemoteServer] into the generic sync engine. The pipeline
 * the engine runs is download → upload → re-download:
 *  - download pulls every server record and upserts it locally (marking it synced),
 *  - upload pushes the records that only exist locally,
 *  - the re-download then re-pulls the just-pushed records so they flip to synced locally.
 *
 * Cursors persist in the shared process-wide DataStore, resolved from the sync store installed once
 * at startup via LBSyncStorage (see SyncDemoRegistry.init).
 */
class DemoItemSyncManager(
    private val localDb: LocalItemDatabase,
    private val server: FakeRemoteServer,
) : LBDefaultSyncManager<ServerItem, LocalItem>() {

    /** Pinned so the persisted cursor key survives a class rename. */
    override val syncKey: SyncKey = SyncKey("DemoItemSyncManager")

    override suspend fun clearData() {
        localDb.clear()
    }

    override suspend fun updateData(data: List<ServerItem>) {
        // Conflicts are isolated per item inside the DB (the diverged item is flagged, not overwritten),
        // so a both-sides edit never fails the sync of the other items.
        localDb.applyServerChanges(data)
    }

    override suspend fun fetchRequest(
        page: Int,
        cursor: String?,
        sinceLastDate: Instant?,
    ): FetchPage<ServerItem, Nothing> = FetchPage(objects = server.fetch())

    override fun updatedAt(obj: ServerItem): Instant = obj.updatedAt

    override fun isInSync(obj: LocalItem): Boolean = obj.isSynced

    override suspend fun objectToBeUploaded(): List<LocalItem> = localDb.unsynced()

    override suspend fun pushObjectsToServer(objects: List<LocalItem>) {
        server.push(objects.map { it.toServerItem() })
    }

    override suspend fun hasSomethingToUpload(): Boolean = localDb.unsynced().isNotEmpty()
}

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

package studio.lunabee.synchronization.roomsyncmanager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.lunabee.synchronization.syncmanager.LBSyncManager
import kotlin.time.Instant

typealias LBGenericRoomSyncManager = LBRoomSyncManager<*, *, *>

/**
 * Default [LBRoomSyncManager] with [Nothing] as page info type.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class LBDefaultRoomSyncManager<ServerData, RoomData : LBRoomSyncModel>(
    dao: LBRoomSyncDao<RoomData>,
    logging: Boolean = true,
    queryDispatcher: CoroutineDispatcher = Dispatchers.IO,
    writeDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LBRoomSyncManager<ServerData, RoomData, Nothing>(
    dao,
    logging,
    queryDispatcher,
    writeDispatcher,
)

/**
 * [LBSyncManager] backed by Room. The DAO is constructor-injected so managers are unit-testable
 * with a fake DAO.
 */
abstract class LBRoomSyncManager<ServerData, RoomData : LBRoomSyncModel, PageInfo>(
    protected val dao: LBRoomSyncDao<RoomData>,
    logging: Boolean = true,
    protected val queryDispatcher: CoroutineDispatcher = Dispatchers.IO,
    protected val writeDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LBSyncManager<ServerData, RoomData, PageInfo>(logging) {

    /**
     * Pulled rows are in sync, so the returned entity **must** carry `lbInSync = true` — there is no
     * mutable `apply` step (the entity is immutable), it must be set in the constructor here.
     */
    protected abstract fun createObjectFrom(serverObject: ServerData): RoomData

    /**
     * Pushes a single entity to the server. Throw on failure: [pushObjectsToServer] catches the first
     * error, marks the already-succeeded subset in sync, then rethrows it.
     *
     * **WARNING**: must run on a background thread. Default no-op (download-only); a bidirectional
     * subclass overrides this.
     *
     * @param obj the entity to push.
     */
    protected open suspend fun push(obj: RoomData) {
    }

    override suspend fun clearData() {
        withContext(writeDispatcher) {
            dao.deleteAll()
        }
    }

    override suspend fun updateData(data: List<ServerData>) {
        val rows = data.map { createObjectFrom(it) }
        withContext(writeDispatcher) {
            dao.upsert(rows)
        }
    }

    override fun isInSync(obj: RoomData): Boolean = obj.lbInSync

    /** Includes soft-deleted rows whose deletion still has to be pushed. */
    override suspend fun objectToBeUploaded(): List<RoomData> {
        return withContext(queryDispatcher) {
            dao.notInSync()
        }
    }

    override suspend fun pushObjectsToServer(objects: List<RoomData>) {
        val syncedIds = mutableListOf<String>()
        var firstError: Exception? = null
        objects.forEach { obj ->
            try {
                push(obj)
                syncedIds += obj.lbLocalId
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (firstError == null) {
                    firstError = e
                }
            }
        }
        if (syncedIds.isNotEmpty()) {
            withContext(writeDispatcher) {
                dao.markInSync(syncedIds)
            }
        }
        firstError?.let { throw it }
    }

    override suspend fun hasSomethingToUpload(): Boolean {
        val objectToUploadCount = objectToBeUploaded().size
        logger?.v("hasSomethingToUpload: nbItem $objectToUploadCount")
        return objectToUploadCount > 0
    }

    /** Default reads [LBRoomSyncModel.lbUpdatedAt] by building the entity; override to read the server
     * object's updated-at directly and skip that. */
    override fun updatedAt(obj: ServerData): Instant? = createObjectFrom(obj).lbUpdatedAt
}

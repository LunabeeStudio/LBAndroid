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

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.lunabee.synchronization.syncmanager.LBSyncManager
import java.util.Date

typealias LBGenericRoomSyncManager = LBRoomSyncManager<*, *, *>

/**
 * Default [LBRoomSyncManager] with [Nothing] as page info type.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class LBDefaultRoomSyncManager<ServerData, RoomData : LBRoomSyncModel>(
    context: Context,
    dao: LBRoomSyncDao<RoomData>,
    logging: Boolean = true,
    queryDispatcher: CoroutineDispatcher = Dispatchers.Main,
    writeDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LBRoomSyncManager<ServerData, RoomData, Nothing>(
    context,
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
    context: Context,
    protected val dao: LBRoomSyncDao<RoomData>,
    logging: Boolean = true,
    protected val queryDispatcher: CoroutineDispatcher = Dispatchers.Main,
    protected val writeDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LBSyncManager<ServerData, RoomData, PageInfo>(context, logging) {

    /**
     * Pulled rows are in sync, so the returned entity **must** carry `lbInSync = true` — there is no
     * mutable `apply` step (the entity is immutable), it must be set in the constructor here.
     */
    protected abstract fun createObjectFrom(serverObject: ServerData): RoomData

    /**
     * **WARNING**: must run on a background thread. Default no-op (download-only); the Parse layer
     * overrides this.
     */
    protected open suspend fun push(obj: RoomData, completion: (error: Exception?) -> Unit) {
        completion(null)
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

    override suspend fun pushObjectsToServer(objects: List<RoomData>, completion: (error: Exception?) -> Unit) {
        val syncedIds = mutableListOf<String>()
        var firstError: Exception? = null
        objects.forEach { obj ->
            val pushError = awaitPush(obj)
            if (pushError == null) {
                syncedIds += obj.lbLocalId
            } else if (firstError == null) {
                firstError = pushError
            }
        }
        if (syncedIds.isNotEmpty()) {
            withContext(writeDispatcher) {
                dao.markInSync(syncedIds)
            }
        }
        completion(firstError)
    }

    override suspend fun hasSomethingToUpload(): Boolean {
        val objectToUploadCount = objectToBeUploaded().size
        logger?.v("hasSomethingToUpload: nbItem $objectToUploadCount")
        return objectToUploadCount > 0
    }

    /** The Parse layer overrides this to read `ParseObject.updatedAt` without building the entity. */
    override fun updatedAt(obj: ServerData): Date? =
        createObjectFrom(obj).lbUpdatedAt?.let { Date(it.toEpochMilliseconds()) }

    private suspend fun awaitPush(obj: RoomData): Exception? {
        val deferred = CompletableDeferred<Exception?>()
        push(obj) { error -> deferred.complete(error) }
        return deferred.await()
    }
}

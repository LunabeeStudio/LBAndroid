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

import studio.lunabee.synchronization.store.SyncKey
import studio.lunabee.synchronization.syncmanager.FetchPage
import studio.lunabee.synchronization.syncmanager.LBSyncManager
import kotlin.time.Instant

/**
 * Download-only [LBSyncManager] mapping a remote backend, reached through [remoteDataSource], onto a local store,
 * reached through [localDataSource]. Neither side is tied to a backend or a database.
 *
 * A download reads every page from the incremental cursor, each one from the keyset [LBRemotePage.nextCursor] of the
 * previous one, then hands all of them to [LBPullLocalDataSource.savePulled] in a single call: the store sees the
 * whole download at once (e.g. to dedupe across pages), and a failure while paging writes nothing. The cursor then
 * moves to the newest `updatedAt` read, page-level [LBRemotePage.maxUpdatedAt] included, and stays put when nothing
 * came back. Incremental checkpoints while paging are therefore not supported: they would save a cursor before its
 * objects are written.
 *
 * Like every manager, it is run through [studio.lunabee.synchronization.LBSyncOperator], one run at a time.
 *
 * @param T the local model.
 * @param syncKey the persisted key of this manager cursor. Several instances of this class must each have their own.
 * @param logging enables the manager logs.
 */
class LBRemotePullSyncManager<T>(
    override val syncKey: SyncKey,
    remoteDataSource: LBPullRemoteDataSource<T>,
    private val localDataSource: LBPullLocalDataSource<T>,
    logging: Boolean = true,
) : LBSyncManager<T, T, LBRemotePage<T>>(logging = logging) {
    private val pullBuffer: RemotePullBuffer<T> = RemotePullBuffer(
        remoteDataSource = remoteDataSource,
        localDataSource = localDataSource,
    )

    override suspend fun clearData() {
        localDataSource.clear()
    }

    override suspend fun fetchRequest(page: Int, cursor: String?, sinceLastDate: Instant?): FetchPage<T, LBRemotePage<T>> =
        pullBuffer.fetch(page = page, cursor = cursor, updatedAfter = sinceLastDate)

    override suspend fun updateData(data: List<T>) {
        pullBuffer.save(data)
    }

    override fun hasNextPage(pageInfo: LBRemotePage<T>): Boolean = pageInfo.hasNextPage

    override fun supportIncrementalSync(): Boolean = false

    override fun updatedAt(obj: T): Instant? = null

    override fun isInSync(obj: T): Boolean = true

    override suspend fun objectToBeUploaded(): List<T> = emptyList()

    override suspend fun pushObjectsToServer(objects: List<T>) = Unit

    override suspend fun hasSomethingToUpload(): Boolean = false
}

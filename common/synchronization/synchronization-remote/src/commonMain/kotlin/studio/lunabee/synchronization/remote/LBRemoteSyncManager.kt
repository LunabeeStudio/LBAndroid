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

/**
 * Two-way [LBRemotePullSyncManager]: on top of the download, it uploads the objects changed on the device.
 *
 * Objects are uploaded one by one, in [LBSyncLocalDataSource.objectsToPush] order: each one is looked up on the
 * backend ([LBSyncRemoteDataSource.findServerId]), then updated when found or created otherwise, then marked pushed
 * ([LBSyncLocalDataSource.markPushed]). The first failure stops the upload and fails the run: the objects already
 * uploaded stay marked, the others stay to upload.
 *
 * @param T the local model.
 * @param syncKey the persisted key of this manager cursor. Several instances of this class must each have their own.
 * @param pushBeforePull `true` to upload before downloading, so an upload failure skips the download (see
 * [uploadBeforeDownload]). Defaults to the engine order: download, upload, then download again.
 * @param logging enables the manager logs.
 */
class LBRemoteSyncManager<T>(
    syncKey: SyncKey,
    private val remoteDataSource: LBSyncRemoteDataSource<T>,
    private val localDataSource: LBSyncLocalDataSource<T>,
    private val pushBeforePull: Boolean = false,
    logging: Boolean = true,
) : LBRemotePullSyncManager<T>(
    syncKey = syncKey,
    remoteDataSource = remoteDataSource,
    localDataSource = localDataSource,
    logging = logging,
) {
    override fun uploadBeforeDownload(): Boolean = pushBeforePull

    override fun isInSync(obj: T): Boolean = false

    override suspend fun objectToBeUploaded(): List<T> = localDataSource.objectsToPush()

    override suspend fun pushObjectsToServer(objects: List<T>) {
        objects.forEach { obj ->
            val serverId = remoteDataSource.findServerId(obj)
            if (serverId == null) {
                remoteDataSource.create(obj)
            } else {
                remoteDataSource.update(serverId = serverId, obj = obj)
            }
            localDataSource.markPushed(obj)
        }
    }

    override suspend fun hasSomethingToUpload(): Boolean = localDataSource.objectsToPush().isNotEmpty()
}

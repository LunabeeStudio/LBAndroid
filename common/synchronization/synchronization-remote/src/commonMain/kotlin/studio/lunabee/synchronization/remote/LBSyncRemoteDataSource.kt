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

/**
 * Remote backend an [LBRemoteSyncManager] downloads from and uploads to, one object at a time.
 */
interface LBSyncRemoteDataSource<T> : LBPullRemoteDataSource<T> {
    /**
     * Looks [obj] up on the backend by the id it has on the device (e.g. an external id column).
     *
     * @return the backend id of the matching object, or `null` when the backend does not hold it yet.
     */
    suspend fun findServerId(obj: T): String?

    /** Creates [obj] on the backend. */
    suspend fun create(obj: T)

    /** Overwrites the backend object [serverId] with [obj]. */
    suspend fun update(serverId: String, obj: T)
}

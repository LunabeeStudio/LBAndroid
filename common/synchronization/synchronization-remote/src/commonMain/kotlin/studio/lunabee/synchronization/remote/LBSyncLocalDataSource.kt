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
 * Local store an [LBRemoteSyncManager] reads the local changes from and records the uploads in.
 */
interface LBSyncLocalDataSource<T> : LBPullLocalDataSource<T> {
    /**
     * @return the objects changed on the device and not uploaded yet, deleted ones included, in upload order.
     */
    suspend fun objectsToPush(): List<T>

    /**
     * Records that [pushed] reached the backend, unless the object changed on the device since [pushed] was read
     * from [objectsToPush]. That later change stays to upload.
     */
    suspend fun markPushed(pushed: T)
}

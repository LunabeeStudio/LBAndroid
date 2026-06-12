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

package studio.lunabee.synchronization.syncmanager

typealias LBDefaultSyncManagerFetchCompletion<ServerData> = LBSyncManagerFetchCompletion<ServerData, Nothing>

/**
 *  Completion called by synchronization manager on fetching data from server
 *
 *  @param ServerData the type of the data returned by the server. Should be the same as [LBSyncManager]'s ServerData type
 *  @param PageInfo the type of the data used for pagination returned by the server. Should be the same as [LBSyncManager]'s PageInfo type
 */
fun interface LBSyncManagerFetchCompletion<ServerData, PageInfo> {
    operator fun invoke(objects: List<ServerData>?) {
        invoke(objects, null)
    }

    operator fun invoke(objects: List<ServerData>?, error: Exception?) {
        invoke(objects, error, null, null)
    }

    operator fun invoke(objects: List<ServerData>?, error: Exception?, pageInfo: PageInfo?, cursor: String?)
}

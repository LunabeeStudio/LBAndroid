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

/**
 * One page of server objects returned by [LBSyncManager.fetchRequest].
 *
 * @param ServerData the server object type, matching [LBSyncManager]'s `ServerData`.
 * @param PageInfo the pagination metadata type, matching [LBSyncManager]'s `PageInfo`. Use [Nothing]
 * when paging is driven solely by object counts.
 * @property objects the page's server objects, **ordered by ascending `updatedAt`** when incremental
 * sync is enabled (the cursor saves the max date seen, so out-of-order results lose records).
 * @property pageInfo optional pagination metadata consumed by `hasNextPage(pageInfo)`; `null` falls back
 * to the object-count heuristic against `queryPageSize()`.
 * @property nextCursor optional opaque cursor forwarded to the next [LBSyncManager.fetchRequest] call.
 */
data class FetchPage<ServerData, PageInfo>(
    val objects: List<ServerData>,
    val pageInfo: PageInfo? = null,
    val nextCursor: String? = null,
)

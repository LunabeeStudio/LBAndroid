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

import kotlin.time.Instant

/**
 * Remote backend an [LBRemotePullSyncManager] downloads from. The implementation owns the transport and the
 * mapping from the remote records to the local model [T].
 */
interface LBPullRemoteDataSource<T> {
    /**
     * Reads one page of the objects updated after [updatedAfter].
     *
     * Pages must be ordered by ascending `updatedAt`, with a stable tie-breaker so that records sharing a date never
     * move between pages, and the filter must be strict (`updatedAt > updatedAfter`).
     *
     * @param page the page index, starting at 0.
     * @param updatedAfter the incremental cursor, or `null` to read every object.
     * @return the page, mapped to the local model.
     */
    suspend fun fetchPage(page: Int, updatedAfter: Instant?): LBRemotePage<T>
}

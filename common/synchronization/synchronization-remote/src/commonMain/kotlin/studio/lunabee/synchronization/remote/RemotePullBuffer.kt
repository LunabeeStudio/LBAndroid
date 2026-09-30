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

import studio.lunabee.synchronization.syncmanager.FetchPage
import kotlin.time.Instant

/**
 * Download side shared by the remote managers: buffers every page of a download and hands them to
 * [LBPullLocalDataSource.savePulled] in a single call once the last page is read.
 */
internal class RemotePullBuffer<T>(
    private val remoteDataSource: LBPullRemoteDataSource<T>,
    private val localDataSource: LBPullLocalDataSource<T>,
) {
    private val pulledObjects: MutableList<T> = mutableListOf()
    private var isLastPageFetched: Boolean = false

    suspend fun fetch(page: Int, cursor: String?, updatedAfter: Instant?): FetchPage<T, LBRemotePage<T>> {
        if (page == 0) pulledObjects.clear()
        val remotePage = remoteDataSource.fetchPage(cursor = cursor, updatedAfter = updatedAfter)
        isLastPageFetched = !remotePage.hasNextPage
        return FetchPage(
            objects = remotePage.objects,
            pageInfo = remotePage,
            nextCursor = remotePage.nextCursor,
            maxUpdatedAt = remotePage.maxUpdatedAt,
        )
    }

    suspend fun save(data: List<T>) {
        pulledObjects += data
        if (isLastPageFetched) {
            if (pulledObjects.isNotEmpty()) localDataSource.savePulled(pulledObjects.toList())
            pulledObjects.clear()
        }
    }
}

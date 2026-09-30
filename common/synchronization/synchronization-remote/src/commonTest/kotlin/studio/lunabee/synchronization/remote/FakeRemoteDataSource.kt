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

internal data class Item(val id: String, val value: Int = 0)

internal data class FetchCall(val cursor: String?, val updatedAfter: Instant?)

/**
 * In-memory backend serving [pages] in order, each page after the one whose [LBRemotePage.nextCursor] is asked for,
 * and recording every call in [calls].
 *
 * @param serverIds backend id of each item the backend already holds, by item id.
 * @param failingCursor cursor whose fetch throws.
 * @param failingPushId item whose create or update throws.
 */
internal class FakeRemoteDataSource(
    private val pages: List<LBRemotePage<Item>> = listOf(LBRemotePage(objects = emptyList(), maxUpdatedAt = null, nextCursor = null)),
    private val serverIds: Map<String, String> = emptyMap(),
    private val failingCursor: String? = null,
    private val failingPushId: String? = null,
) : LBSyncRemoteDataSource<Item> {
    val calls: MutableList<String> = mutableListOf()
    val fetchCalls: MutableList<FetchCall> = mutableListOf()

    override suspend fun fetchPage(cursor: String?, updatedAfter: Instant?): LBRemotePage<Item> {
        calls += "fetch"
        fetchCalls += FetchCall(cursor = cursor, updatedAfter = updatedAfter)
        if (cursor != null && cursor == failingCursor) throw RemoteException(message = "fetch $cursor")
        if (cursor == null) return pages.first()
        val previous = pages.indexOfFirst { page -> page.nextCursor == cursor }
        if (previous == -1) error("unknown cursor $cursor")
        return pages[previous + 1]
    }

    override suspend fun findServerId(obj: Item): String? {
        calls += "find ${obj.id}"
        return serverIds[obj.id]
    }

    override suspend fun create(obj: Item) {
        calls += "create ${obj.id}"
        if (obj.id == failingPushId) throw RemoteException(message = "create ${obj.id}")
    }

    override suspend fun update(serverId: String, obj: Item) {
        calls += "update $serverId"
        if (obj.id == failingPushId) throw RemoteException(message = "update ${obj.id}")
    }
}

internal class RemoteException(message: String) : Exception(message)

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

package studio.lunabee.synchronization.parsektorroom.remote

import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import studio.lunabee.parse.client.LBParseClient
import studio.lunabee.parse.query.ParseObject
import studio.lunabee.parse.query.pull
import studio.lunabee.parse.query.pullWhere
import studio.lunabee.parse.query.query
import studio.lunabee.synchronization.parsektorroom.LBParseRoomClass
import studio.lunabee.synchronization.remote.LBRemotePage
import studio.lunabee.synchronization.remote.LBSyncRemoteDataSource
import studio.lunabee.synchronization.roomsyncmanager.LBRoomSyncModel
import kotlin.time.Instant

/**
 * [LBSyncRemoteDataSource] on a Parse class, reached through [parse]. Failures surface as any parse-ktor call does.
 * It never deletes: a deleted row is pushed as an update.
 */
internal class LBParseRemoteDataSource<D : ParseObject, R : LBRoomSyncModel>(
    private val parse: LBParseClient,
    private val declaration: LBParseRoomClass<D, R>,
) : LBSyncRemoteDataSource<R> {
    private val className: String = declaration.tableParseName()

    override suspend fun fetchPage(cursor: String?, updatedAfter: Instant?): LBRemotePage<R> {
        val page = parse.pull(
            className = className,
            where = pullWhere(updatedAfter = updatedAfter),
            cursor = cursor,
            serializer = declaration.serializer,
            keys = declaration.keysToSelect(),
            include = declaration.keysToInclude(),
            transform = declaration::createObjectFrom,
        )
        return LBRemotePage(objects = page.items, maxUpdatedAt = page.maxUpdatedAt, nextCursor = page.nextCursor)
    }

    override suspend fun findServerId(obj: R): String? {
        obj.lbServerId?.let { return it }
        val localIdKey = checkNotNull(declaration.localIdKey) { "localIdKey is required to push" }
        return parse.query<ServerId>(
            className = className,
            where = buildJsonObject { put(localIdKey, obj.lbLocalId) },
            order = null,
            limit = 1,
            keys = listOf(ObjectIdKey),
        ).firstOrNull()?.objectId
    }

    override suspend fun create(obj: R) {
        parse.httpClient.post("classes/$className") { setBody(body(obj)) }
    }

    override suspend fun update(serverId: String, obj: R) {
        parse.httpClient.put("classes/$className/$serverId") { setBody(body(obj)) }
    }

    private fun body(obj: R): JsonObject {
        val encoded = PushJson.encodeToJsonElement(declaration.serializer, declaration.update(obj)).jsonObject
        return JsonObject(encoded.filterKeys { it !in ReservedKeys })
    }

    @Serializable
    private data class ServerId(val objectId: String)

    private companion object {
        const val ObjectIdKey: String = "objectId"
        val PushJson: Json = Json { encodeDefaults = true }
        val ReservedKeys: Set<String> = setOf(ObjectIdKey, "createdAt", "updatedAt")
    }
}

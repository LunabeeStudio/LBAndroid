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

package studio.lunabee.synchronization.parsektorroom

import kotlinx.serialization.Serializable
import studio.lunabee.parse.model.ParseObjectId
import studio.lunabee.parse.query.ParseObject
import studio.lunabee.synchronization.roomsyncmanager.LBRoomSyncModel
import kotlin.time.Instant

internal data class TestRow(
    override val lbLocalId: String,
    override val lbServerId: String? = null,
    override val lbUpdatedAt: Instant? = null,
    override val lbInSync: Boolean = false,
    override val lbDeleted: Boolean = false,
    val name: String = "",
) : LBRoomSyncModel

@Serializable
internal data class TestDto(
    override val objectId: ParseObjectId? = null,
    override val updatedAt: Instant? = null,
    val name: String,
    val localId: String?,
    val deleted: Boolean,
    val count: Int = 0,
) : ParseObject

internal class TestClass(
    override val localIdKey: String? = "localId",
) : LBParseRoomClass<TestDto, TestRow>(TestDto.serializer()) {
    override fun tableParseName(): String = "Item"

    override fun keysToSelect(): List<String> = listOf("name", "localId", "deleted")

    override fun keysToInclude(): List<String> = listOf("owner")

    override fun createObjectFrom(serverObject: TestDto): TestRow = TestRow(
        lbLocalId = serverObject.localId ?: checkNotNull(serverObject.objectId).value,
        lbServerId = serverObject.objectId?.value,
        lbUpdatedAt = serverObject.updatedAt,
        lbInSync = true,
        lbDeleted = serverObject.deleted,
        name = serverObject.name,
    )

    override fun update(from: TestRow): TestDto = TestDto(
        objectId = from.lbServerId?.let(::ParseObjectId),
        updatedAt = from.lbUpdatedAt,
        name = from.name,
        localId = from.lbLocalId,
        deleted = from.lbDeleted,
    )
}

internal fun dtoJson(index: Int): String {
    val id = index.toString().padStart(3, '0')
    return """{"objectId":"o$id","updatedAt":"2026-09-21T10:00:00.${id}Z","name":"n$id","localId":null,"deleted":false}"""
}

internal fun resultsBody(indices: IntRange): String = indices.joinToString(
    prefix = """{"results":[""",
    postfix = "]}",
    transform = ::dtoJson,
)

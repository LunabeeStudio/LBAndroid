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

package studio.lunabee.parse.query

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import studio.lunabee.parse.model.ParseDateSerializer
import kotlin.time.Instant

/**
 * Keyset position in a pull sorted by [PullOrder]: the next page starts after the object updated at [updatedAt] with
 * id [objectId]. A keyset, not an offset, since an offset skips an object whenever another one is updated mid-pull.
 */
@PublishedApi
internal data class PullCursor(
    val updatedAt: Instant,
    val objectId: String,
) {
    fun encode(): String = "$updatedAt$Separator$objectId"

    fun after(where: JsonObject): JsonObject {
        val date = Json.encodeToJsonElement(ParseDateSerializer, updatedAt)
        val sortedAfter = buildJsonArray {
            addJsonObject { putJsonObject(UpdatedAtField) { put(GreaterThanOperator, date) } }
            addJsonObject {
                put(UpdatedAtField, date)
                putJsonObject(ObjectIdField) { put(GreaterThanOperator, objectId) }
            }
        }
        val keysetWhere = JsonObject(mapOf(OrOperator to sortedAfter))
        return if (OrOperator in where) {
            buildJsonObject {
                putJsonArray(key = AndOperator) {
                    add(where)
                    add(keysetWhere)
                }
            }
        } else {
            JsonObject(where + keysetWhere)
        }
    }

    companion object {
        private const val Separator: Char = '|'

        fun decode(cursor: String): PullCursor = PullCursor(
            updatedAt = Instant.parse(cursor.substringBefore(Separator)),
            objectId = cursor.substringAfter(Separator),
        )

        /** Position of [parseObject], whose missing objectId sorts first on the server, like the empty string. */
        fun of(parseObject: ParseObject): PullCursor = PullCursor(
            updatedAt = checkNotNull(parseObject.updatedAt) { "Pulled Parse object without updatedAt" },
            objectId = parseObject.objectId?.value.orEmpty(),
        )
    }
}

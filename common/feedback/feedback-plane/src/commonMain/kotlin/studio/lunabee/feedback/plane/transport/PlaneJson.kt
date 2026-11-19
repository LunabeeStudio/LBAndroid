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

package studio.lunabee.feedback.plane.transport

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import studio.lunabee.feedback.core.transport.LBFeedbackError

internal class PlaneResponse(
    val status: Int,
    private val body: String,
) {
    fun json(): PlaneJson {
        val parsed = try {
            Json.parseToJsonElement(body) as? JsonObject
        } catch (e: IllegalArgumentException) {
            throw LBFeedbackError.Server(statusCode = status, cause = e)
        }
        return PlaneJson(status, parsed ?: throw LBFeedbackError.Server(statusCode = status))
    }
}

internal class PlaneJson(
    private val status: Int,
    private val json: JsonObject,
) {
    fun string(key: String): String = (json[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: shapeError()

    fun int(key: String): Int = (json[key] as? JsonPrimitive)?.intOrNull ?: shapeError()

    fun obj(key: String): PlaneJson = PlaneJson(status, json[key] as? JsonObject ?: shapeError())

    fun optionalString(key: String): String? = (json[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    fun optionalObj(key: String): PlaneJson? = (json[key] as? JsonObject)?.let { PlaneJson(status, it) }

    fun strings(): Map<String, String> = json.mapValues { (_, value) ->
        (value as? JsonPrimitive)?.content ?: shapeError()
    }

    private fun shapeError(): Nothing = throw LBFeedbackError.Server(statusCode = status)
}

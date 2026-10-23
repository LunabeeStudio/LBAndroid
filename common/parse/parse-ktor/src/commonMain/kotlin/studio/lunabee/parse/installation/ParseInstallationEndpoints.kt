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

package studio.lunabee.parse.installation

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import studio.lunabee.parse.client.LBParseClient
import studio.lunabee.parse.model.ParseObjectId

@Serializable
private data class CreatedInstallation(
    val objectId: ParseObjectId,
)

/**
 * Creates an installation from its [fields] (for example `installationId`, `deviceType`, `deviceToken`), and returns
 * its [ParseObjectId].
 */
suspend fun LBParseClient.createInstallation(fields: JsonObject): ParseObjectId = httpClient.post("installations") {
    setBody(fields)
}.body<CreatedInstallation>().objectId

/**
 * Updates the installation [objectId] with [fields].
 */
suspend fun LBParseClient.updateInstallation(objectId: ParseObjectId, fields: JsonObject) {
    httpClient.put("installations/${objectId.value}") { setBody(fields) }
}

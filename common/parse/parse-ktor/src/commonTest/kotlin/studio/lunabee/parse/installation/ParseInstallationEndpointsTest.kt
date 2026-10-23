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

import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.lunabee.parse.model.ParseObjectId
import studio.lunabee.parse.test.FakeParseServer
import studio.lunabee.parse.test.assertJsonBody
import studio.lunabee.parse.test.assertRequest
import studio.lunabee.parse.test.respondParse
import kotlin.test.Test
import kotlin.test.assertEquals

class ParseInstallationEndpointsTest {
    private val fields = buildJsonObject {
        put("installationId", "i1")
        put("deviceType", "android")
    }

    @Test
    fun createInstallationPostsFieldsAndReturnsObjectId() = runTest {
        val server = FakeParseServer(handler = { respondParse("""{"objectId":"inst1","createdAt":"2026-09-21T10:00:00.000Z"}""") })

        val objectId = server.client.createInstallation(fields)

        assertEquals(ParseObjectId("inst1"), objectId)
        assertRequest(server.lastRequest, HttpMethod.Post, "installations")
        assertJsonBody(server.lastRequest, """{"installationId":"i1","deviceType":"android"}""")
    }

    @Test
    fun updateInstallationPutsFieldsOnObjectId() = runTest {
        val server = FakeParseServer(handler = { respondParse("""{"updatedAt":"2026-09-21T10:00:00.000Z"}""") })

        server.client.updateInstallation(ParseObjectId("inst1"), fields)

        assertRequest(server.lastRequest, HttpMethod.Put, "installations/inst1")
        assertJsonBody(server.lastRequest, """{"installationId":"i1","deviceType":"android"}""")
    }
}

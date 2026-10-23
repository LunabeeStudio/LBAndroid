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

package studio.lunabee.parse.file

import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import studio.lunabee.parse.test.FakeParseServer
import studio.lunabee.parse.test.assertRequest
import studio.lunabee.parse.test.respondParse
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ParseFileUploadTest {
    @Test
    fun uploadFileSendsRawBytesWithContentType() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        val server = FakeParseServer(
            handler = { respondParse("""{"name":"abc_avatar.jpg","url":"https://files.test/abc_avatar.jpg"}""") },
        )

        val file = server.client.uploadFile("avatar.jpg", bytes, ContentType.Image.JPEG)

        assertRequest(server.lastRequest, HttpMethod.Post, "files/avatar.jpg")
        assertEquals("image/jpeg", server.lastRequest.body.contentType.toString())
        assertContentEquals(bytes, server.lastRequest.body.toByteArray())
        assertEquals("abc_avatar.jpg", file.name)
        assertEquals("https://files.test/abc_avatar.jpg", file.url)
    }
}

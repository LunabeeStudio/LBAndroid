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

package studio.lunabee.monitoring.ui.share

import studio.lunabee.monitoring.core.LBPayload
import studio.lunabee.monitoring.core.LBRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class LBRequestExtTest {
    private val request = LBRequest(
        id = Uuid.random(),
        host = "api.example.com",
        methodName = "POST",
        statusCode = 201,
        sendingAt = Instant.fromEpochSeconds(epochSeconds = 0),
        duration = 123.milliseconds,
        path = "/users",
        queryParams = "page=1",
        outgoingPayload = LBPayload(headers = "Content-Type=application/json", body = "{\"name\":\"John\"}"),
        incomingPayload = null,
    )

    @Test
    fun toShareText_formats_request_and_response_test() {
        val expected = """
            |POST api.example.com/users
            |Query: page=1
            |Status: 201
            |Duration: 123 ms
            |Sent at: Thu, 1 Jan 1970 00:00:00 GMT
            |
            |*Request*
            |Headers:
            |```
            |Content-Type=application/json
            |```
            |Body:
            |```
            |{"name":"John"}
            |```
            |
            |*Response*
            |No payload
        """.trimMargin()
        assertEquals(expected = expected, actual = request.toShareText())
    }

    @Test
    fun toShareText_adds_missing_leading_slash_to_path_test() {
        val text = request.copy(path = "api/v2/facts").toShareText()
        assertTrue(text.startsWith("POST api.example.com/api/v2/facts\n"))
    }

    @Test
    fun toShareText_truncates_long_body_test() {
        val longBody = "a".repeat(n = ShareBodyMaxLength + 10)
        val text = request.copy(incomingPayload = LBPayload(body = longBody)).toShareText()
        assertTrue(text.contains("… (truncated, ${longBody.length} characters)"))
        assertFalse(text.contains("a".repeat(n = ShareBodyMaxLength + 1)))
    }

    @Test
    fun toShareText_marks_empty_headers_and_body_test() {
        val text = request.copy(incomingPayload = LBPayload()).toShareText()
        assertTrue(text.endsWith("*Response*\nHeaders: empty\nBody: empty"))
    }
}

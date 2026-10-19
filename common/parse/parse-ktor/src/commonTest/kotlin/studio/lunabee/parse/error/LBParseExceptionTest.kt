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

package studio.lunabee.parse.error

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import studio.lunabee.parse.test.FakeParseServer
import studio.lunabee.parse.test.respondParse
import studio.lunabee.parse.test.respondParseError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class LBParseExceptionTest {
    @Serializable
    private data class Payload(val value: Int)

    @Test
    fun malformedSuccessBodyThrowsDecoding() = runTest {
        val server = FakeParseServer(handler = { respondParse("""{"value":"not a number"}""") })

        assertFailsWith<LBParseException.Decoding> { server.client.httpClient.get("config").body<Payload>() }
    }

    @Test
    fun parseErrorBodyThrowsParseWithCodeAndMessage() = runTest {
        val server = FakeParseServer(
            handler = { respondParseError(code = ParseErrorCode.ScriptFailed, error = "Coach not found") },
        )

        val error = assertFailsWith<LBParseException.Parse> { server.client.httpClient.get("config") }

        assertEquals(141, error.code)
        assertEquals("Coach not found", error.message)
        assertEquals(400, error.httpStatus)
    }

    @Test
    fun nonJsonErrorBodyThrowsHttpWithStatus() = runTest {
        val server = FakeParseServer(
            handler = { respondParse(body = "<html>Bad gateway</html>", status = HttpStatusCode.BadGateway) },
        )

        val error = assertFailsWith<LBParseException.Http> { server.client.httpClient.get("config") }

        assertEquals(502, error.httpStatus)
        assertEquals("<html>Bad gateway</html>", error.body)
    }

    @Test
    fun ioExceptionThrowsNetwork() = runTest {
        val server = FakeParseServer(handler = { throw IOException("offline") })

        val error = assertFailsWith<LBParseException.Network> { server.client.httpClient.get("config") }

        assertIs<IOException>(error.cause?.cause)
    }

    @Test
    fun requestTimeoutThrowsNetwork() = runTest {
        val server = FakeParseServer(
            handler = { throw HttpRequestTimeoutException(url = "https://parse.test/parse/config", timeoutMillis = 1_000) },
        )

        assertFailsWith<LBParseException.Network> { server.client.httpClient.get("config") }
    }
}

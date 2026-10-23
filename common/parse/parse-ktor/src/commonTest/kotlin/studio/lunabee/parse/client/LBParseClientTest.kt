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

package studio.lunabee.parse.client

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import studio.lunabee.parse.error.LBParseException
import studio.lunabee.parse.test.FakeParseServer
import studio.lunabee.parse.test.assertRequest
import studio.lunabee.parse.test.respondParse
import studio.lunabee.parse.test.respondParseError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class LBParseClientTest {
    @Serializable
    private data class Payload(val value: Int)

    @Test
    fun everyRequestCarriesCredentials() = runTest {
        val server = FakeParseServer()

        server.client.httpClient.get("config")

        assertRequest(server.lastRequest, HttpMethod.Get, "config")
    }

    @Test
    fun sessionTokenIsSentWhenProvided() = runTest {
        val server = FakeParseServer(sessionToken = "r:token")

        server.client.httpClient.get("config")

        assertEquals("r:token", server.lastRequest.headers["X-Parse-Session-Token"])
    }

    @Test
    fun sessionTokenIsAbsentWhenProviderReturnsNull() = runTest {
        val server = FakeParseServer(sessionToken = null)

        server.client.httpClient.get("config")

        assertNull(server.lastRequest.headers["X-Parse-Session-Token"])
    }

    @Test
    fun withoutSessionTokenOmitsTheHeader() = runTest {
        val server = FakeParseServer()

        server.client.httpClient.post("login") { withoutSessionToken() }

        assertRequest(server.lastRequest, HttpMethod.Post, "login", sessionToken = null)
    }

    @Test
    fun installationIdIsSentWhenProvided() = runTest {
        val server = FakeParseServer(installationId = "installation-1")

        server.client.httpClient.get("config")

        assertEquals("installation-1", server.lastRequest.headers["X-Parse-Installation-Id"])
    }

    @Test
    fun installationIdIsAbsentWithoutProvider() = runTest {
        val server = FakeParseServer()

        server.client.httpClient.get("config")

        assertNull(server.lastRequest.headers["X-Parse-Installation-Id"])
    }

    @Test
    fun mapErrConvertsTheThrownError() = runTest {
        class AppError(val source: LBParseException) : Throwable()
        val server = FakeParseServer(
            mapErr = { AppError(it) },
            handler = { respondParseError(code = 141, error = "Coach not found") },
        )

        val error = assertFailsWith<AppError> { server.client.httpClient.get("config") }

        assertEquals(141, (error.source as LBParseException.Parse).code)
    }

    @Test
    fun mapErrConvertsBodyDecodingFailure() = runTest {
        class AppError(val source: LBParseException) : Throwable()
        val server = FakeParseServer(
            mapErr = { AppError(it) },
            handler = { respondParse("""{"value":"not a number"}""") },
        )

        val error = assertFailsWith<AppError> { server.client.httpClient.get("config").body<Payload>() }

        assertIs<LBParseException.Decoding>(error.source)
    }

    @Test
    fun ioExceptionIsRetriedOnceThenSucceeds() = runTest {
        var failed = false
        val server = FakeParseServer(
            handler = {
                if (!failed) {
                    failed = true
                    throw IOException("offline")
                }
                respondParse("{}")
            },
        )

        server.client.httpClient.get("config")

        assertEquals(2, server.requests.size)
    }

    @Test
    fun ioExceptionTwiceThrowsNetworkAfterTwoRequests() = runTest {
        val server = FakeParseServer(handler = { throw IOException("offline") })

        assertFailsWith<LBParseException.Network> { server.client.httpClient.get("config") }

        assertEquals(2, server.requests.size)
    }

    @Test
    fun postIsRetriedLikeOtherMethods() = runTest {
        val server = FakeParseServer(handler = { throw IOException("offline") })

        assertFailsWith<LBParseException.Network> { server.client.httpClient.post("functions/hello") }

        assertEquals(2, server.requests.size)
    }

    @Test
    fun maxRetriesZeroSendsOnce() = runTest {
        val server = FakeParseServer(maxRetries = 0, handler = { throw IOException("offline") })

        assertFailsWith<LBParseException.Network> { server.client.httpClient.get("config") }

        assertEquals(1, server.requests.size)
    }

    @Test
    fun serverErrorIsNotRetried() = runTest {
        val server = FakeParseServer(handler = { respondParse(body = "oops", status = HttpStatusCode.InternalServerError) })

        assertFailsWith<LBParseException.Http> { server.client.httpClient.get("config") }

        assertEquals(1, server.requests.size)
    }

    @Test
    fun clientErrorIsNotRetried() = runTest {
        val server = FakeParseServer(handler = { respondParse(body = "oops", status = HttpStatusCode.NotFound) })

        assertFailsWith<LBParseException.Http> { server.client.httpClient.get("config") }

        assertEquals(1, server.requests.size)
    }

    @Test
    fun parseErrorBodyIsNotRetried() = runTest {
        val server = FakeParseServer(handler = { respondParseError(code = 141, error = "Coach not found") })

        assertFailsWith<LBParseException.Parse> { server.client.httpClient.get("config") }

        assertEquals(1, server.requests.size)
    }

    @Test
    fun closeClosesEngine() {
        val engine = MockEngine { throw IOException("unused") }
        val client = LBParseClient(
            engine = engine,
            config = LBParseConfig(
                serverUrl = FakeParseServer.ServerUrl,
                applicationId = FakeParseServer.ApplicationId,
                clientKey = FakeParseServer.ClientKey,
                sessionTokenProvider = { null },
            ),
        )

        client.close()

        assertFalse(engine.isActive)
    }

    @Test
    fun serverUrlWithTrailingSlashKeepsMountPath() = runTest {
        var path: String? = null
        val client = LBParseClient(
            engine = MockEngine { request ->
                path = request.url.encodedPath
                respondParse("{}")
            },
            config = LBParseConfig(
                serverUrl = "${FakeParseServer.ServerUrl}/",
                applicationId = FakeParseServer.ApplicationId,
                clientKey = FakeParseServer.ClientKey,
                sessionTokenProvider = { null },
            ),
        )

        client.httpClient.get("config")

        assertEquals("/parse/config", path)
    }
}

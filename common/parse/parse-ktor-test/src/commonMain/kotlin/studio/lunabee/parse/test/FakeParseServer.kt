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

package studio.lunabee.parse.test

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.lunabee.parse.client.LBParseClient
import studio.lunabee.parse.client.LBParseConfig
import studio.lunabee.parse.error.LBParseException
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.update

/**
 * Parse server running a real [LBParseClient] on a Ktor `MockEngine`, recording every request it receives.
 *
 * [handler] answers each request, so multi-call flows can return a different response per path.
 * Throw an `IOException` from it to simulate a network failure.
 *
 * @param sessionToken Session token returned by the client session token provider.
 * @param installationId Installation id returned by the client installation id provider, none by default.
 * @param maxRetries See [LBParseConfig.maxRetries].
 * @param mapErr See [LBParseConfig.mapErr].
 * @param handler Answer to each request, `{}` with status 200 by default.
 */
@OptIn(ExperimentalAtomicApi::class)
class FakeParseServer(
    sessionToken: String? = SessionToken,
    installationId: String? = null,
    maxRetries: Int = 1,
    mapErr: suspend (LBParseException) -> Throwable = { it },
    var handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respondParse("{}") },
) {
    private val receivedRequests: AtomicReference<List<HttpRequestData>> = AtomicReference(emptyList())

    /** Client under test, talking to this server. */
    val client: LBParseClient = LBParseClient(
        engine = MockEngine { request ->
            receivedRequests.update { it + request }
            handler(request)
        },
        config = LBParseConfig(
            serverUrl = ServerUrl,
            applicationId = ApplicationId,
            clientKey = ClientKey,
            sessionTokenProvider = { sessionToken },
            installationIdProvider = installationId?.let { id -> { id } },
            maxRetries = maxRetries,
            mapErr = mapErr,
        ),
    )

    /** Every request received so far, in order. */
    val requests: List<HttpRequestData>
        get() = receivedRequests.load()

    /** Last request received. */
    val lastRequest: HttpRequestData
        get() = requests.last()

    companion object {
        /** Server URL of the client, with a `/parse` mount path. */
        const val ServerUrl: String = "https://parse.test/parse"

        /** Application id sent by the client. */
        const val ApplicationId: String = "test-application-id"

        /** Client key sent by the client. */
        const val ClientKey: String = "test-client-key"

        /** Default session token returned by the client session token provider. */
        const val SessionToken: String = "r:test-session-token"
    }
}

/**
 * Answers with a Parse JSON [body].
 */
fun MockRequestHandleScope.respondParse(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(
    content = body,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

/**
 * Answers with a Parse `{code, error}` error body.
 */
fun MockRequestHandleScope.respondParseError(
    code: Int,
    error: String,
    status: HttpStatusCode = HttpStatusCode.BadRequest,
): HttpResponseData = respondParse(
    body = buildJsonObject {
        put("code", code)
        put("error", error)
    }.toString(),
    status = status,
)

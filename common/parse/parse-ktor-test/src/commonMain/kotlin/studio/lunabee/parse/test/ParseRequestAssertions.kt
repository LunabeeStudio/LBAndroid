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

import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Asserts [request] targets [path] under the [FakeParseServer] server URL with [method], carries the Parse credentials
 * and the expected [sessionToken] header (`null` for no header).
 */
fun assertRequest(
    request: HttpRequestData,
    method: HttpMethod,
    path: String,
    sessionToken: String? = FakeParseServer.SessionToken,
) {
    assertEquals(method, request.method)
    assertEquals("/parse/$path", request.url.encodedPath)
    assertEquals(FakeParseServer.ApplicationId, request.headers["X-Parse-Application-Id"])
    assertEquals(FakeParseServer.ClientKey, request.headers["X-Parse-Client-Key"])
    assertEquals(sessionToken, request.headers["X-Parse-Session-Token"])
}

/**
 * Asserts the `where`, `order` and `limit` parameters of [request], `where` compared as JSON, with no `skip`.
 */
fun assertQuery(
    request: HttpRequestData,
    where: String,
    order: String?,
    limit: Int,
) {
    val parameters = request.url.parameters
    assertEquals(parseJson(where), parseJson(checkNotNull(parameters["where"]) { "where parameter missing" }))
    assertEquals(order, parameters["order"])
    assertEquals(limit.toString(), parameters["limit"])
    assertNull(parameters["skip"])
}

/**
 * Asserts the body of [request] equals the [expected] JSON, ignoring formatting.
 */
suspend fun assertJsonBody(request: HttpRequestData, expected: String) {
    assertEquals(parseJson(expected), parseJson(request.body.toByteArray().decodeToString()))
}

private fun parseJson(text: String): JsonElement = Json.parseToJsonElement(text)

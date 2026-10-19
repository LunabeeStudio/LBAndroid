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

package studio.lunabee.parse.user

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import studio.lunabee.parse.client.LBParseClient
import studio.lunabee.parse.client.withoutSessionToken
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Creates the user [username], sending no session token. [additionalFields] are sent along with the credentials.
 */
suspend fun LBParseClient.signUp(
    username: String,
    password: String,
    additionalFields: JsonObject = JsonObject(emptyMap()),
): ParseUser = httpClient.post("users") {
    withoutSessionToken()
    setBody(
        JsonObject(
            additionalFields + buildJsonObject {
                put(key = "username", value = username)
                put(key = "password", value = password)
            },
        ),
    )
}.body<ParseUser>()

/**
 * Logs the user [username] in, sending no session token.
 */
suspend fun LBParseClient.logIn(username: String, password: String): ParseUser = httpClient.post("login") {
    withoutSessionToken()
    setBody(
        buildJsonObject {
            put(key = "username", value = username)
            put(key = "password", value = password)
        },
    )
}.body<ParseUser>()

/**
 * Creates or resumes an anonymous user identified by [anonymousId], a random UUID by default, sending no session token.
 * Keep the id to resume the same user later.
 */
@OptIn(ExperimentalUuidApi::class)
suspend fun LBParseClient.logInAnonymously(anonymousId: String = Uuid.random().toString()): ParseUser =
    httpClient.post("users") {
        withoutSessionToken()
        setBody(
            buildJsonObject {
                putJsonObject(key = "authData") {
                    putJsonObject(key = "anonymous") { put(key = "id", value = anonymousId) }
                }
            },
        )
    }.body<ParseUser>()

/**
 * Ends the session of the current user.
 */
suspend fun LBParseClient.logOut() {
    httpClient.post("logout") { setBody(JsonObject(emptyMap())) }
}

/**
 * Current user, identified by the session token.
 */
suspend fun LBParseClient.me(): ParseUser = httpClient.get("users/me").body<ParseUser>()

/**
 * Asks Parse to email a password reset link to [email], sending no session token.
 */
suspend fun LBParseClient.requestPasswordReset(email: String) {
    httpClient.post("requestPasswordReset") {
        withoutSessionToken()
        setBody(buildJsonObject { put("email", email) })
    }
}

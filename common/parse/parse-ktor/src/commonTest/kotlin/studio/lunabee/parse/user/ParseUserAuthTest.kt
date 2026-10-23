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

import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import studio.lunabee.parse.test.FakeParseServer
import studio.lunabee.parse.test.assertJsonBody
import studio.lunabee.parse.test.assertRequest
import studio.lunabee.parse.test.respondParse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ParseUserAuthTest {
    private val userBody = """{"objectId":"u1","sessionToken":"r:s1","username":"ada"}"""

    private fun server(): FakeParseServer = FakeParseServer(handler = { respondParse(userBody) })

    @Test
    fun signUpPostsCredentialsWithoutSessionToken() = runTest {
        val server = server()

        val user = server.client.signUp(username = "ada", password = "secret")

        assertEquals(ParseUser(objectId = "u1", sessionToken = "r:s1"), user)
        assertRequest(server.lastRequest, HttpMethod.Post, "users", sessionToken = null)
        assertJsonBody(server.lastRequest, """{"username":"ada","password":"secret"}""")
    }

    @Test
    fun logInPostsCredentialsWithoutSessionToken() = runTest {
        val server = server()

        server.client.logIn(username = "ada", password = "secret")

        assertRequest(server.lastRequest, HttpMethod.Post, "login", sessionToken = null)
        assertJsonBody(server.lastRequest, """{"username":"ada","password":"secret"}""")
    }

    @Test
    fun logInAnonymouslyPostsAuthDataAndReturnsSessionToken() = runTest {
        val server = server()

        val user = server.client.logInAnonymously(anonymousId = "anon-1")

        assertEquals("r:s1", user.sessionToken)
        assertRequest(server.lastRequest, HttpMethod.Post, "users", sessionToken = null)
        assertJsonBody(server.lastRequest, """{"authData":{"anonymous":{"id":"anon-1"}}}""")
    }

    @Test
    fun logInAnonymouslyGeneratesAnIdByDefault() = runTest {
        val server = server()

        server.client.logInAnonymously()
        server.client.logInAnonymously()

        val ids = server.requests.map { it.body.toByteArray().decodeToString() }
        assertNotEquals(ids.first(), ids.last())
    }

    @Test
    fun logOutPostsWithSessionToken() = runTest {
        val server = server()

        server.client.logOut()

        assertRequest(server.lastRequest, HttpMethod.Post, "logout")
    }

    @Test
    fun meGetsWithSessionToken() = runTest {
        val server = server()

        server.client.me()

        assertRequest(server.lastRequest, HttpMethod.Get, "users/me")
    }

    @Test
    fun requestPasswordResetPostsEmailWithoutSessionToken() = runTest {
        val server = server()

        server.client.requestPasswordReset(email = "ada@test.com")

        assertRequest(server.lastRequest, HttpMethod.Post, "requestPasswordReset", sessionToken = null)
        assertJsonBody(server.lastRequest, """{"email":"ada@test.com"}""")
    }

    @Test
    fun signUpThenLogInThenMeAnswerPerPath() = runTest {
        val server = FakeParseServer(
            handler = { request ->
                when (request.url.encodedPath) {
                    "/parse/users" -> respondParse("""{"objectId":"u1"}""")
                    "/parse/login" -> respondParse(userBody)
                    else -> respondParse("""{"objectId":"u1","username":"ada"}""")
                }
            },
        )

        val signedUp = server.client.signUp(username = "ada", password = "secret")
        val loggedIn = server.client.logIn(username = "ada", password = "secret")
        val me = server.client.me()

        assertEquals(null, signedUp.sessionToken)
        assertEquals("r:s1", loggedIn.sessionToken)
        assertEquals("u1", me.objectId)
        assertEquals(listOf("/parse/users", "/parse/login", "/parse/users/me"), server.requests.map { it.url.encodedPath })
    }
}

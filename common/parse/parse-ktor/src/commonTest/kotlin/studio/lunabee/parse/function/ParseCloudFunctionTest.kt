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

package studio.lunabee.parse.function

import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import studio.lunabee.parse.test.FakeParseServer
import studio.lunabee.parse.test.assertJsonBody
import studio.lunabee.parse.test.assertRequest
import studio.lunabee.parse.test.respondParse
import kotlin.test.Test
import kotlin.test.assertEquals

class ParseCloudFunctionTest {
    @Serializable
    private data class Greeting(val message: String)

    @Serializable
    private data class Name(val name: String)

    @Test
    fun callFunctionPostsBodyAndUnwrapsResult() = runTest {
        val server = FakeParseServer(handler = { respondParse("""{"result":{"message":"hello Ada"}}""") })

        val greeting = server.client.callFunction<Greeting, Name>("hello", Name("Ada"))

        assertEquals(Greeting("hello Ada"), greeting)
        assertRequest(server.lastRequest, HttpMethod.Post, "functions/hello")
        assertJsonBody(server.lastRequest, """{"name":"Ada"}""")
    }
}

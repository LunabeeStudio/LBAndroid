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

package studio.lunabee.feedback.plane.transport

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import studio.lunabee.feedback.core.model.LBFeedbackAttachment
import studio.lunabee.feedback.core.model.LBFeedbackStatus
import studio.lunabee.feedback.core.model.LBFeedbackType
import studio.lunabee.feedback.core.transport.LBFeedbackError
import studio.lunabee.feedback.plane.fakeDraft
import studio.lunabee.feedback.test.fakeMetadata
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LBPlaneFeedbackTransportTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val bodies = mutableListOf<String>()
    private var handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respondJson("{}") }

    private val transport = LBPlaneFeedbackTransport(
        baseUrl = "https://plane.test/",
        workspaceSlug = "lunabee",
        projectId = "proj",
        token = { "secret" },
        httpClient = HttpClient(
            MockEngine { request ->
                requests += request
                bodies += request.body.toByteArray().decodeToString()
                handler(request)
            },
        ),
    )

    private fun MockRequestHandleScope.respondJson(
        json: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData = respond(
        content = json,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    @Test
    fun sendCreatesIntakeItemAndReturnsWorkItemId() = runTest {
        handler = { respondJson("""{"id":"intake-1","issue":"work-1","status":-2}""", HttpStatusCode.Created) }

        val id = transport.send(fakeDraft(type = LBFeedbackType.Idea, title = "More"), fakeMetadata())

        val request = requests.single()
        assertEquals("work-1", id)
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://plane.test/api/v1/workspaces/lunabee/projects/proj/intake-issues/", request.url.toString())
        assertEquals("secret", request.headers["X-API-Key"])
        assertContains(bodies.single(), "\"name\":\"[Idea] More\"")
        assertContains(bodies.single(), "\"priority\":\"none\"")
        assertContains(bodies.single(), "description_html")
    }

    @Test
    fun getMapsStatusAndTitle() = runTest {
        val expected = mapOf(
            -2 to LBFeedbackStatus.Pending,
            -1 to LBFeedbackStatus.Declined,
            0 to LBFeedbackStatus.Snoozed,
            1 to LBFeedbackStatus.Accepted,
            2 to LBFeedbackStatus.Duplicate,
        )
        expected.forEach { (code, status) ->
            handler = { respondJson("""{"status":$code,"issue_detail":{"name":"[Bug] Crash"}}""") }

            val state = transport.get("work-1")

            assertEquals(status, state.status)
            assertEquals("[Bug] Crash", state.title)
        }
        assertEquals(HttpMethod.Get, requests.first().method)
        assertTrue(requests.first().url.toString().endsWith("/intake-issues/work-1/"))
    }

    @Test
    fun updateSendsRegeneratedDescription() = runTest {
        transport.update("work-1", fakeDraft(body = "new <b>", reporter = "Ada"), fakeMetadata())

        assertEquals(HttpMethod.Patch, requests.single().method)
        assertTrue(requests.single().url.toString().endsWith("/intake-issues/work-1/"))
        assertContains(bodies.single(), "new &lt;b&gt;")
        assertContains(bodies.single(), "Ada")
    }

    @Test
    fun deleteCallsDelete() = runTest {
        handler = { respond(content = "", status = HttpStatusCode.NoContent) }

        transport.delete("work-1")

        assertEquals(HttpMethod.Delete, requests.single().method)
        assertTrue(requests.single().url.toString().endsWith("/intake-issues/work-1/"))
    }

    @Test
    fun attachRunsRegisterUploadConfirmInOrder() = runTest {
        handler = { request ->
            when (request.method) {
                HttpMethod.Post if request.url.host == "plane.test" -> respondJson(
                    """{"upload_data":{"url":"https://s3.test/bucket","fields":{"key":"k1","policy":"p1"}},"asset_id":"asset-1"}""",
                )

                HttpMethod.Post -> respond(content = "", status = HttpStatusCode.NoContent)

                else -> respondJson("{}")
            }
        }
        val attachment = LBFeedbackAttachment(bytes = "PNGDATA".encodeToByteArray(), name = "shot.png", mimeType = "image/png")

        transport.attach("work-1", attachment)

        assertEquals(3, requests.size)
        assertTrue(requests[0].url.toString().endsWith("/work-items/work-1/attachments/"))
        assertContains(bodies[0], "\"name\":\"shot.png\"")
        assertContains(bodies[0], "\"type\":\"image/png\"")
        assertContains(bodies[0], "\"size\":7")
        assertEquals("secret", requests[0].headers["X-API-Key"])

        assertEquals("https://s3.test/bucket", requests[1].url.toString())
        assertNull(requests[1].headers["X-API-Key"])
        assertContains(bodies[1], "name=\"key\"")
        assertContains(bodies[1], "k1")
        assertContains(bodies[1], "name=\"policy\"")
        assertContains(bodies[1], "PNGDATA")
        assertTrue(bodies[1].indexOf("name=\"policy\"") < bodies[1].indexOf("name=\"file\""))

        assertEquals(HttpMethod.Patch, requests[2].method)
        assertTrue(requests[2].url.toString().endsWith("/work-items/work-1/attachments/asset-1/"))
        assertContains(bodies[2], "\"is_uploaded\":true")
    }

    @Test
    fun attachRegistrationRefusedWith400MapsToAttachmentRejected() = runTest {
        handler = { respondJson("""{"error":"Invalid file type."}""", HttpStatusCode.BadRequest) }
        val attachment = LBFeedbackAttachment(bytes = ByteArray(1), name = "a.xyz", mimeType = "application/x-weird")

        assertFailsWith<LBFeedbackError.AttachmentRejected> { transport.attach("work-1", attachment) }

        assertEquals(1, requests.size)
    }

    @Test
    fun attachOverFiveMegabytesFailsWithoutCall() = runTest {
        val attachment = LBFeedbackAttachment(ByteArray(5 * 1024 * 1024 + 1), "big.png", "image/png")

        assertFailsWith<LBFeedbackError.TooLarge> { transport.attach("work-1", attachment) }

        assertTrue(requests.isEmpty())
    }

    @Test
    fun httpStatusesMapToErrors() = runTest {
        suspend fun codeFails(code: Int, check: (LBFeedbackError) -> Unit) {
            handler = { respond(content = "", status = HttpStatusCode.fromValue(code)) }
            check(assertFailsWith<LBFeedbackError> { transport.delete("work-1") })
        }

        codeFails(401) { assertTrue(it is LBFeedbackError.Unauthorized) }
        codeFails(403) { assertTrue(it is LBFeedbackError.Unauthorized) }
        codeFails(429) { assertTrue(it is LBFeedbackError.RateLimited) }
        codeFails(404) { assertTrue(it is LBFeedbackError.NotFound) }
        codeFails(413) { assertTrue(it is LBFeedbackError.TooLarge) }
        codeFails(500) { assertEquals(500, (it as LBFeedbackError.Server).statusCode) }
    }

    @Test
    fun responseWithUnexpectedShapeMapsToServer() = runTest {
        handler = { respondJson("""{"issue_detail":{"name":"x"}}""") }
        assertFailsWith<LBFeedbackError.Server> { transport.get("work-1") }

        handler = { respondJson("""{"status":"nope"}""") }
        assertFailsWith<LBFeedbackError.Server> { transport.get("work-1") }

        handler = { respondJson("""{"status":42}""") }
        assertFailsWith<LBFeedbackError.Server> { transport.get("work-1") }

        handler = { respondJson("not json") }
        assertFailsWith<LBFeedbackError.Server> { transport.send(fakeDraft(), fakeMetadata()) }

        handler = { respondJson("""{"id":"intake-only"}""", HttpStatusCode.Created) }
        assertFailsWith<LBFeedbackError.Server> { transport.send(fakeDraft(), fakeMetadata()) }
    }

    @Test
    fun ioFailureMapsToNetwork() = runTest {
        handler = { throw IOException("offline") }

        val error = assertFailsWith<LBFeedbackError.Network> { transport.delete("work-1") }

        assertFalse(error.message.isNullOrBlank())
    }
}

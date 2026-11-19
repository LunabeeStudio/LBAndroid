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
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import studio.lunabee.feedback.core.client.LBFeedbackClient
import studio.lunabee.feedback.core.client.LBFeedbackSendResult
import studio.lunabee.feedback.core.metadata.LBFeedbackMetadataProvider
import studio.lunabee.feedback.core.model.LBFeedbackAttachment
import studio.lunabee.feedback.core.model.LBFeedbackDraft
import studio.lunabee.feedback.core.model.LBFeedbackType
import studio.lunabee.feedback.test.FakeLBFeedbackStore
import studio.lunabee.feedback.test.fakeMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LBPlaneAttachmentsTest {
    private val methods = mutableListOf<String>()

    private val client = LBFeedbackClient(
        transport = LBPlaneFeedbackTransport(
            baseUrl = "https://plane.test",
            workspaceSlug = "lunabee",
            projectId = "proj",
            token = { "secret" },
            httpClient = HttpClient(
                MockEngine { request ->
                    methods += "${request.method.value} ${request.url.encodedPath}"
                    val json = headersOf(HttpHeaders.ContentType, "application/json")
                    when {
                        request.url.encodedPath.endsWith("/intake-issues/") ->
                            respond("""{"issue":"work-1"}""", HttpStatusCode.Created, json)

                        request.method == HttpMethod.Post && request.url.host == "plane.test" -> respond(
                            """{"upload_data":{"url":"https://s3.test/b","fields":{"key":"k"}},"asset_id":"asset"}""",
                            HttpStatusCode.OK,
                            json,
                        )

                        else -> respond("{}", HttpStatusCode.OK, json)
                    }
                },
            ),
        ),
        store = FakeLBFeedbackStore(),
        metadataProvider = LBFeedbackMetadataProvider { fakeMetadata() },
    )

    @Test
    fun twoAttachmentsRunSixAttachCallsAfterCreate() = runTest {
        val files = listOf("a.txt", "b.txt").map { LBFeedbackAttachment(ByteArray(3), it, "text/plain") }

        val result = client.send(LBFeedbackDraft(LBFeedbackType.Bug, "T", "B", attachments = files))

        assertIs<LBFeedbackSendResult.Sent>(result)
        assertEquals(7, methods.size)
        assertEquals("POST /api/v1/workspaces/lunabee/projects/proj/intake-issues/", methods.first())
        val perFile = listOf(
            "POST /api/v1/workspaces/lunabee/projects/proj/work-items/work-1/attachments/",
            "POST /b",
            "PATCH /api/v1/workspaces/lunabee/projects/proj/work-items/work-1/attachments/asset/",
        )
        assertEquals(perFile + perFile, methods.drop(1))
    }
}

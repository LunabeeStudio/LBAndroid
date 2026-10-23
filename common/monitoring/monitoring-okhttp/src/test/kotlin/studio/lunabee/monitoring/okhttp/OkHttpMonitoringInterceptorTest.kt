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

package studio.lunabee.monitoring.okhttp

import androidx.paging.PagingData
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import studio.lunabee.monitoring.core.LBMonitoring
import studio.lunabee.monitoring.core.LBRequest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class OkHttpMonitoringInterceptorTest {
    private val server = MockWebServer()
    private val monitoring = RecordingMonitoring()
    private val client = OkHttpClient.Builder()
        .addNetworkInterceptor(OkHttpMonitoringInterceptor(monitoring = monitoring))
        .build()

    @BeforeTest
    fun setUp() {
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    @Test
    fun binary_request_body_containing_a_percent_sign_goes_through_and_is_not_recorded_test() {
        server.enqueue(jsonResponse("""{"name":"file.jpg"}"""))
        val picture = byteArrayOf(0x25, 0x00, 0xFF.toByte(), 0x25, 0x7A).toRequestBody("image/jpeg".toMediaType())

        val response = client.newCall(Request.Builder().url(server.url("/files/file")).post(picture).build()).execute()

        assertEquals(200, response.code)
        assertEquals("<body not recorded: image/jpeg>", monitoring.awaitResponse().outgoingPayload?.body)
    }

    @Test
    fun non_json_text_response_containing_a_percent_sign_is_recorded_as_is_test() {
        server.enqueue(MockResponse.Builder().body("100% down").addHeader("Content-Type", "text/plain").build())

        client.newCall(Request.Builder().url(server.url("/status")).build()).execute().close()

        assertEquals("100% down", monitoring.awaitResponse().incomingPayload?.body)
    }

    @Test
    fun json_response_is_recorded_pretty_printed_test() {
        server.enqueue(jsonResponse("""{"result":{"discount":"50%"}}"""))

        client.newCall(Request.Builder().url(server.url("/functions/getChallenges")).build()).execute().close()

        val expected = """
            {
                "result": {
                    "discount": "50%"
                }
            }
        """.trimIndent()
        assertEquals(expected, monitoring.awaitResponse().incomingPayload?.body)
    }

    @Test
    fun large_response_body_is_capped_and_still_fully_delivered_test() {
        val body = """{"results":"${"x".repeat(300_000)}"}"""
        server.enqueue(jsonResponse(body))

        val delivered = client.newCall(Request.Builder().url(server.url("/batch")).build()).execute().body.string()

        assertEquals(body, delivered)
        assertTrue(monitoring.awaitResponse().incomingPayload?.body.orEmpty().length <= 100_000)
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse.Builder().body(body).addHeader("Content-Type", "application/json").build()
}

@OptIn(ExperimentalUuidApi::class)
private class RecordingMonitoring : LBMonitoring {
    private val upserts = Channel<LBRequest>(Channel.UNLIMITED)

    fun awaitResponse(): LBRequest = runBlocking {
        withTimeout(5.seconds) { upserts.consumeAsFlow().first { it.incomingPayload != null } }
    }

    override suspend fun upsertRequest(request: LBRequest) {
        upserts.send(request)
    }

    override fun getPagingRequests(): Flow<PagingData<LBRequest>> = error("unused")
    override fun getRequestsPaginatedAsFlow(pageSize: Int, skip: Int): Flow<List<LBRequest>> = error("unused")
    override fun getRequestsPaginatedAsFlow(): Flow<List<LBRequest>> = error("unused")
    override suspend fun getRequestsPaginated(pageSize: Int, skip: Int): List<LBRequest> = error("unused")
    override fun getRequestByIdAsFlow(requestId: Uuid): Flow<LBRequest> = error("unused")
    override suspend fun getRequestById(requestId: Uuid): LBRequest = error("unused")
    override suspend fun removeRequestById(requestId: Uuid): Unit = error("unused")
    override suspend fun flush(): Unit = error("unused")
    override fun getRequestCountAsFlow(): Flow<Int> = error("unused")
    override suspend fun getRequestCount(): Int = error("unused")
}

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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import studio.lunabee.logger.LBLogger
import studio.lunabee.logger.w
import studio.lunabee.monitoring.core.LBMonitoring
import studio.lunabee.monitoring.core.LBPayload
import studio.lunabee.monitoring.core.LBRequest
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val logger = LBLogger.get<OkHttpMonitoringInterceptor>()

/**
 * Longest body recorded, in bytes. Keeps each stored request well under the 2 MB Android CursorWindow limit, which a
 * monitoring list query reads whole rows into.
 */
private const val MaxBodyBytes: Long = 100_000L

private val TextSubtypes: List<String> = listOf("json", "xml", "x-www-form-urlencoded", "javascript", "graphql")

/**
 * An interceptor to record OkHttp calls
 *
 * Only textual bodies (`text/…`, JSON, XML, form…) are recorded, capped at [MaxBodyBytes]; other bodies
 * are replaced by a placeholder naming their content type. Recording never throws into the call.
 */
class OkHttpMonitoringInterceptor(
    private val monitoring: LBMonitoring,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Interceptor {
    private val prettyJson = Json { prettyPrint = true }
    private val coroutineScope = CoroutineScope(
        context = dispatcher + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            logger.w(throwable, "Unable to record request")
        },
    )

    @OptIn(ExperimentalUuidApi::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val lbRequest = LBRequest(
            id = Uuid.random(),
            host = request.url.host,
            methodName = request.method,
            queryParams = request.url.encodedQuery,
            outgoingPayload = LBPayload(
                headers = request.headers.toString(),
                body = request.body?.let(::requestBodyText),
                size = (request.body?.contentLength() ?: 0) + request.headers.byteCount(),
            ),
            incomingPayload = null,
            statusCode = -1,
            sendingAt = Clock.System.now(),
            duration = Duration.ZERO,
            path = request.url.encodedPath,
        )

        val requestJob = coroutineScope.launch {
            monitoring.upsertRequest(lbRequest)
        }

        val response = chain.proceed(request)

        val peekedBody = response.peekBody(MaxBodyBytes)
        val lbResponse = lbRequest.copy(
            incomingPayload = LBPayload(
                headers = response.headers.toString(),
                body = responseBodyText(contentType = response.body.contentType(), peekedBody = peekedBody),
                size = response.body.contentLength().coerceAtLeast(peekedBody.contentLength()) +
                    response.headers.byteCount(),
            ),
            statusCode = response.code,
            duration = Clock.System.now() - lbRequest.sendingAt,
        )

        coroutineScope.launch {
            requestJob.join()
            monitoring.upsertRequest(lbResponse)
        }

        return response
    }

    private fun requestBodyText(body: RequestBody): String {
        if (!isText(body.contentType()) || body.isOneShot() || body.isDuplex()) return notRecordedBodyText(body.contentType())
        val buffer = Buffer()
        body.writeTo(buffer)
        return prettify(buffer.readUtf8(minOf(buffer.size, MaxBodyBytes)))
    }

    private fun responseBodyText(contentType: MediaType?, peekedBody: ResponseBody): String =
        if (isText(contentType)) prettify(peekedBody.string()) else notRecordedBodyText(contentType)

    private fun prettify(body: String): String =
        runCatching { prettyJson.encodeToString(prettyJson.parseToJsonElement(body)) }.getOrDefault(body)

    private fun isText(contentType: MediaType?): Boolean =
        contentType != null && (contentType.type == "text" || TextSubtypes.any { it in contentType.subtype })

    private fun notRecordedBodyText(contentType: MediaType?): String = "<body not recorded: ${contentType ?: "unknown type"}>"
}

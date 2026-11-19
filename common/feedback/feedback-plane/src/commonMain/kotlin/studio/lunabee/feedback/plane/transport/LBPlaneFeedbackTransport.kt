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
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.lunabee.feedback.core.model.LBFeedbackAttachment
import studio.lunabee.feedback.core.model.LBFeedbackDraft
import studio.lunabee.feedback.core.model.LBFeedbackMetadata
import studio.lunabee.feedback.core.model.LBFeedbackRemoteState
import studio.lunabee.feedback.core.model.LBFeedbackStatus
import studio.lunabee.feedback.core.model.LBFeedbackType
import studio.lunabee.feedback.core.transport.LBFeedbackError
import studio.lunabee.feedback.core.transport.LBFeedbackTransport
import studio.lunabee.feedback.plane.description.LBPlaneDescriptionBuilder

private const val ApiKeyHeader: String = "X-API-Key"

/**
 * [LBFeedbackTransport] sending feedback to the Intake queue of a Plane project, with the Plane REST API v1.
 *
 * The remote id is the id of the work item behind the intake entry. Failures are thrown as [LBFeedbackError].
 *
 * Plane only accepts the MIME types of its `ATTACHMENT_MIME_TYPES` setting. In v1.5.0 they are images (jpeg, png, gif,
 * svg+xml, webp, tiff, bmp), pdf, Office and OpenDocument files, text/plain, text/markdown, text/csv, application/json,
 * xml, rtf, audio, video, zip and other archives, fonts and application/octet-stream. A file of another type makes the
 * upload fail with [LBFeedbackError.AttachmentRejected]. Use `text/plain` for logs.
 *
 * @param baseUrl root URL of the Plane server, for example `https://plane.example.com`
 * @param workspaceSlug slug of the Plane workspace
 * @param projectId id of the Plane project
 * @param token returns the API token, read for each request
 * @param httpClient client owned by the app, which also picks the HTTP engine. It is never closed here. Do not install
 * automatic retries on POST requests, it would duplicate items.
 */
class LBPlaneFeedbackTransport(
    baseUrl: String,
    workspaceSlug: String,
    projectId: String,
    private val token: suspend () -> String,
    private val httpClient: HttpClient,
) : LBFeedbackTransport {
    private val projectUrl: String = "${baseUrl.trimEnd('/')}/api/v1/workspaces/$workspaceSlug/projects/$projectId"

    override suspend fun send(draft: LBFeedbackDraft, metadata: LBFeedbackMetadata): String {
        val body = buildJsonObject {
            put(
                "issue",
                buildJsonObject {
                    put("name", title(draft))
                    put("description_html", LBPlaneDescriptionBuilder.build(draft, metadata))
                    put("priority", "none")
                },
            )
        }
        return execute(HttpMethod.Post, "$projectUrl/intake-issues/") { setJson(body) }.json().string("issue")
    }

    override suspend fun get(remoteId: String): LBFeedbackRemoteState {
        val response = execute(HttpMethod.Get, "$projectUrl/intake-issues/$remoteId/")
        val json = response.json()
        return LBFeedbackRemoteState(
            status = LBFeedbackStatus.fromCode(json.int("status")) ?: throw LBFeedbackError.Server(statusCode = response.status),
            title = json.optionalObj("issue_detail")?.optionalString("name").orEmpty(),
        )
    }

    override suspend fun update(remoteId: String, draft: LBFeedbackDraft, metadata: LBFeedbackMetadata) {
        val body = buildJsonObject {
            put(
                "issue",
                buildJsonObject {
                    put("name", title(draft))
                    put("description_html", LBPlaneDescriptionBuilder.build(draft, metadata))
                },
            )
        }
        execute(HttpMethod.Patch, "$projectUrl/intake-issues/$remoteId/") { setJson(body) }
    }

    override suspend fun delete(remoteId: String) {
        execute(HttpMethod.Delete, "$projectUrl/intake-issues/$remoteId/")
    }

    override suspend fun attach(remoteId: String, attachment: LBFeedbackAttachment) {
        if (attachment.bytes.size > LBFeedbackAttachment.MaxBytes) throw LBFeedbackError.TooLarge()
        val attachmentsUrl = "$projectUrl/work-items/$remoteId/attachments/"

        val registration = buildJsonObject {
            put("name", attachment.name)
            put("type", attachment.mimeType)
            put("size", attachment.bytes.size)
        }
        val created = try {
            execute(HttpMethod.Post, attachmentsUrl) { setJson(registration) }.json()
        } catch (e: LBFeedbackError.Server) {
            throw if (e.statusCode == 400) LBFeedbackError.AttachmentRejected(e) else e
        }
        val uploadData = created.obj("upload_data")
        val assetId = created.string("asset_id")
        val uploadUrl = uploadData.string("url")
        val fields = uploadData.obj("fields").strings()

        val form = MultiPartFormDataContent(
            formData {
                fields.forEach { (key, value) -> append(key, value) }
                append(
                    key = "file",
                    value = attachment.bytes,
                    headers = Headers.build {
                        append(HttpHeaders.ContentType, attachment.mimeType)
                        append(HttpHeaders.ContentDisposition, "filename=\"${attachment.name.replace("\"", "")}\"")
                    },
                )
            },
        )
        execute(HttpMethod.Post, uploadUrl, authenticated = false) { setBody(form) }

        val confirmation = buildJsonObject { put("is_uploaded", true) }
        execute(HttpMethod.Patch, "$attachmentsUrl$assetId/") { setJson(confirmation) }
    }

    private fun title(draft: LBFeedbackDraft): String {
        val prefix = when (draft.type) {
            LBFeedbackType.Bug -> "Bug"
            LBFeedbackType.Idea -> "Idea"
            LBFeedbackType.Other -> "Other"
        }
        return "[$prefix] ${draft.title}"
    }

    private fun HttpRequestBuilder.setJson(body: JsonObject) {
        setBody(TextContent(body.toString(), ContentType.Application.Json))
    }

    private suspend fun execute(
        method: HttpMethod,
        url: String,
        authenticated: Boolean = true,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): PlaneResponse {
        val apiKey = if (authenticated) token() else null
        val response = try {
            val httpResponse = httpClient.request {
                this.method = method
                url(url)
                expectSuccess = false
                if (apiKey != null) header(ApiKeyHeader, apiKey)
                configure()
            }
            PlaneResponse(status = httpResponse.status.value, body = httpResponse.bodyAsText())
        } catch (e: IOException) {
            throw LBFeedbackError.Network(e)
        }
        if (response.status in SuccessRange) return response
        throw errorFor(response.status)
    }

    private fun errorFor(code: Int): LBFeedbackError = when (code) {
        401, 403 -> LBFeedbackError.Unauthorized()
        404 -> LBFeedbackError.NotFound()
        413 -> LBFeedbackError.TooLarge()
        429 -> LBFeedbackError.RateLimited()
        else -> LBFeedbackError.Server(statusCode = code)
    }
}

private val SuccessRange: IntRange = 200..299

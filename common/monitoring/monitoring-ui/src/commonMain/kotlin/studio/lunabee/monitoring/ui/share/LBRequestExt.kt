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

package studio.lunabee.monitoring.ui.share

import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import studio.lunabee.monitoring.core.LBPayload
import studio.lunabee.monitoring.core.LBRequest

/**
 * Maximum number of characters kept for each body, so the shared text stays under the platform share limits
 * (e.g. the Android binder transaction limit).
 */
internal const val ShareBodyMaxLength: Int = 100_000

/**
 * Build a plain text, Markdown-friendly (Slack, GitHub, etc.) representation of the request and its response.
 */
internal fun LBRequest.toShareText(): String = buildString {
    // Ktor records the path without its leading slash, OkHttp with it.
    val safePath = path?.takeIf { it.isNotEmpty() }?.let { if (it.startsWith(prefix = "/")) it else "/$it" }.orEmpty()
    appendLine("$methodName $host$safePath")
    queryParams?.takeIf { it.isNotBlank() }?.let { appendLine("Query: $it") }
    appendLine("Status: $statusCode")
    appendLine("Duration: ${duration.inWholeMilliseconds} ms")
    appendLine("Sent at: ${sendingAt.format(DateTimeComponents.Formats.RFC_1123)}")
    appendPayload(title = "Request", payload = outgoingPayload)
    appendPayload(title = "Response", payload = incomingPayload)
}.trimEnd()

private fun StringBuilder.appendPayload(title: String, payload: LBPayload?) {
    appendLine()
    appendLine("*$title*")
    if (payload == null) {
        appendLine("No payload")
        return
    }
    appendBlock(title = "Headers", content = payload.headers)
    appendBlock(title = "Body", content = payload.body?.truncate())
}

private fun StringBuilder.appendBlock(title: String, content: String?) {
    if (content.isNullOrBlank()) {
        appendLine("$title: empty")
    } else {
        appendLine("$title:")
        appendLine("```")
        appendLine(content.trimEnd())
        appendLine("```")
    }
}

private fun String.truncate(): String = if (length > ShareBodyMaxLength) {
    take(n = ShareBodyMaxLength) + "\n… (truncated, $length characters)"
} else {
    this
}

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

package studio.lunabee.feedback.plane.description

import studio.lunabee.feedback.core.model.LBFeedbackDraft
import studio.lunabee.feedback.core.model.LBFeedbackMetadata

/**
 * Builds the `description_html` of a Plane intake item from the user text and the metadata. Every value is escaped.
 */
object LBPlaneDescriptionBuilder {
    /**
     * Returns the HTML description: body, reporter line when set, then a table of the metadata and its extras.
     */
    fun build(draft: LBFeedbackDraft, metadata: LBFeedbackMetadata): String = buildString {
        if (draft.body.isNotBlank()) {
            append("<p>")
            append(draft.body.lines().joinToString(separator = "<br>") { escape(it) })
            append("</p>")
        }
        draft.reporter?.takeIf { it.isNotBlank() }?.let {
            append("<p><strong>Reporter:</strong> ${escape(it)}</p>")
        }
        append("<table><tbody>")
        rows(metadata).forEach { (key, value) ->
            append("<tr><td>${escape(key)}</td><td>${escape(value)}</td></tr>")
        }
        append("</tbody></table>")
    }

    internal fun escape(text: String): String = buildString(text.length) {
        text.forEach {
            when (it) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                '\r' -> Unit
                else -> append(it)
            }
        }
    }

    private fun rows(metadata: LBFeedbackMetadata): List<Pair<String, String>> = listOf(
        "App version" to "${metadata.appVersionName} (${metadata.appVersionCode})",
        "Build variant" to metadata.buildVariant,
        "Device" to "${metadata.deviceManufacturer} ${metadata.deviceModel}",
        "OS" to metadata.osVersion,
        "Locale" to metadata.locale,
        "Library version" to metadata.libraryVersion,
        "Sent at (UTC)" to metadata.sentAt.toString(),
    ) + metadata.extras.toList()
}

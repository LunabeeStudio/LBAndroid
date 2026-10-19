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

package studio.lunabee.parse.file

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import studio.lunabee.parse.client.LBParseClient
import studio.lunabee.parse.model.ParseFile

/**
 * Uploads [bytes] as the file [name] with the given [contentType] (for example `ContentType.Image.JPEG`), and returns the
 * [ParseFile] holding the name and URL given by the server.
 */
suspend fun LBParseClient.uploadFile(name: String, bytes: ByteArray, contentType: ContentType): ParseFile =
    httpClient.post("files/$name") {
        contentType(contentType)
        setBody(bytes)
    }.body<ParseFile>()

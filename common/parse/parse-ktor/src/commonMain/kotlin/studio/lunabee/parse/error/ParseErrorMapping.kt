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

package studio.lunabee.parse.error

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import studio.lunabee.ktor.LBKtorException

private val errorJson: Json = Json { ignoreUnknownKeys = true }

internal suspend fun LBKtorException.toParseException(): LBParseException = when (this) {
    is LBKtorException.IO -> LBParseException.Network(cause = this)
    is LBKtorException.ServerResponse -> response.toParseException(cause = this)
    is LBKtorException.Decoding -> LBParseException.Decoding(cause = this)
    is LBKtorException.Unexpected -> LBParseException.Unexpected(cause = this)
}

private suspend fun HttpResponse.toParseException(cause: Throwable): LBParseException {
    val text = bodyAsText()
    val body = try {
        errorJson.decodeFromString<ParseErrorBody>(text)
    } catch (_: SerializationException) {
        null
    }
    return if (body != null) {
        LBParseException.Parse(
            code = body.code,
            error = body.error ?: "HTTP ${status.value}",
            httpStatus = status.value,
            cause = cause,
        )
    } else {
        LBParseException.Http(httpStatus = status.value, body = text, cause = cause)
    }
}

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

/**
 * Failure of a Parse request. Every error raised by `LBParseClient` is an [LBParseException], unless
 * `LBParseConfig.mapErr` converts it to an app type.
 */
sealed class LBParseException(
    message: String?,
    cause: Throwable?,
) : Exception(message, cause) {
    /**
     * The request did not reach the server or got no answer: no connectivity, connect, socket or request timeout.
     */
    class Network(cause: Throwable) : LBParseException(message = cause.message, cause = cause)

    /**
     * A non-2xx response carrying a Parse `{code, error}` body.
     *
     * @property code Parse error code, see [ParseErrorCode].
     * @property error Error text of the server, also used as the exception message.
     * @property httpStatus HTTP status of the response.
     */
    class Parse(
        val code: Int,
        val error: String,
        val httpStatus: Int,
        cause: Throwable? = null,
    ) : LBParseException(message = error, cause = cause)

    /**
     * A non-2xx response without a Parse body, for example an HTML error page from a proxy.
     *
     * @property httpStatus HTTP status of the response.
     * @property body Raw response body.
     */
    class Http(
        val httpStatus: Int,
        val body: String,
        cause: Throwable? = null,
    ) : LBParseException(message = "HTTP $httpStatus", cause = cause)

    /**
     * A response body that could not be decoded into the expected type.
     */
    class Decoding(cause: Throwable) : LBParseException(message = cause.message, cause = cause)

    /**
     * Any other failure.
     */
    class Unexpected(cause: Throwable) : LBParseException(message = cause.message, cause = cause)
}

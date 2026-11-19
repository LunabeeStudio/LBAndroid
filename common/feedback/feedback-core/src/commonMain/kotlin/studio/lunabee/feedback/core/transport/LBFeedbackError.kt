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

package studio.lunabee.feedback.core.transport

/**
 * Failure of an [LBFeedbackTransport] or `LBFeedbackClient` call, thrown by their functions.
 */
sealed class LBFeedbackError(message: String?, cause: Throwable?) : Exception(message, cause) {
    /** The credentials were refused. */
    class Unauthorized(cause: Throwable? = null) : LBFeedbackError("Unauthorized", cause)

    /** Too many requests were sent, try again later. */
    class RateLimited(cause: Throwable? = null) : LBFeedbackError("Rate limited", cause)

    /** The server could not be reached. */
    class Network(cause: Throwable? = null) : LBFeedbackError(cause?.message ?: "Network error", cause)

    /** The uploaded content is over the size limit. */
    class TooLarge(cause: Throwable? = null) : LBFeedbackError("Content too large", cause)

    /**
     * The server failed to handle the request.
     *
     * @property statusCode HTTP status code of the response
     */
    class Server(val statusCode: Int, cause: Throwable? = null) : LBFeedbackError("Server error $statusCode", cause)

    /** The server refused an attachment, for example because its type is not accepted. */
    class AttachmentRejected(cause: Throwable? = null) : LBFeedbackError("Attachment rejected", cause)

    /** The item is no longer pending, so it can neither be edited nor deleted. */
    class NotEditable(cause: Throwable? = null) : LBFeedbackError("Not editable", cause)

    /** The item does not exist, or is not visible to the caller. */
    class NotFound(cause: Throwable? = null) : LBFeedbackError("Not found", cause)
}

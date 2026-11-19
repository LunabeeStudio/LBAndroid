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

package studio.lunabee.feedback.core.model

/**
 * Triage state of a feedback item on the remote intake queue.
 *
 * @property code numeric value used by the Plane intake API
 */
enum class LBFeedbackStatus(val code: Int) {
    /** Waiting for triage. Only a pending item can be edited or deleted. */
    Pending(-2),

    /** Rejected by the product owner. */
    Declined(-1),

    /** Postponed by the product owner. */
    Snoozed(0),

    /** Accepted by the product owner. */
    Accepted(1),

    /** Marked as a duplicate of another item. */
    Duplicate(2),
    ;

    companion object {
        /**
         * Returns the status matching [code], or `null` when [code] is unknown.
         */
        fun fromCode(code: Int): LBFeedbackStatus? = entries.firstOrNull { it.code == code }
    }
}

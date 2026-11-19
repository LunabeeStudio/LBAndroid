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

package studio.lunabee.feedback.core.store

import kotlinx.coroutines.flow.Flow
import studio.lunabee.feedback.core.model.LBFeedbackItem

/**
 * Local memory of the feedback sent from this install, and of the reporter name.
 */
interface LBFeedbackStore {
    /** Sent items, newest first. */
    val items: Flow<List<LBFeedbackItem>>

    /** Name remembered for the next feedback, `null` when none. */
    val reporterName: Flow<String?>

    /** Remembers [item]. */
    suspend fun add(item: LBFeedbackItem)

    /**
     * Replaces the stored item [remoteId] with the result of [transform], applied to its current value in one atomic
     * step so a concurrent change to another field is not lost. Does nothing when there is no such item.
     */
    suspend fun update(remoteId: String, transform: (LBFeedbackItem) -> LBFeedbackItem)

    /** Forgets the item [remoteId]. */
    suspend fun remove(remoteId: String)

    /** Remembers [name], or forgets the name when `null`. */
    suspend fun setReporterName(name: String?)
}

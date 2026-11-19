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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import studio.lunabee.feedback.core.model.LBFeedbackItem

/**
 * [LBFeedbackStore] that keeps everything in memory and forgets it with the process. For tests and previews.
 *
 * @param initial items present at the start, newest first
 */
class InMemoryLBFeedbackStore(initial: List<LBFeedbackItem> = emptyList()) : LBFeedbackStore {
    private val state = MutableStateFlow(initial)
    private val reporter = MutableStateFlow<String?>(null)

    override val items: Flow<List<LBFeedbackItem>> = state

    override val reporterName: Flow<String?> = reporter

    override suspend fun add(item: LBFeedbackItem) = state.update { listOf(item) + it }

    override suspend fun update(remoteId: String, transform: (LBFeedbackItem) -> LBFeedbackItem) = state.update { items ->
        items.map { if (it.remoteId == remoteId) transform(it) else it }
    }

    override suspend fun remove(remoteId: String) = state.update { items -> items.filterNot { it.remoteId == remoteId } }

    override suspend fun setReporterName(name: String?) {
        reporter.value = name
    }
}

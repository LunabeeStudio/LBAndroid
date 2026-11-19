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

package studio.lunabee.compose.demo.feedback

import kotlinx.coroutines.delay
import studio.lunabee.feedback.core.model.LBFeedbackAttachment
import studio.lunabee.feedback.core.model.LBFeedbackDraft
import studio.lunabee.feedback.core.model.LBFeedbackMetadata
import studio.lunabee.feedback.core.model.LBFeedbackRemoteState
import studio.lunabee.feedback.core.model.LBFeedbackStatus
import studio.lunabee.feedback.core.transport.LBFeedbackError
import studio.lunabee.feedback.core.transport.LBFeedbackTransport

class FakeFeedbackTransport : LBFeedbackTransport {
    private class Remote(var title: String, var status: LBFeedbackStatus)

    private val remotes: MutableMap<String, Remote> = mutableMapOf()
    private var nextId: Int = 1

    var simulateNetworkError: Boolean = false

    override suspend fun send(draft: LBFeedbackDraft, metadata: LBFeedbackMetadata): String {
        simulate()
        val id = "fake-${nextId++}"
        remotes[id] = Remote(title = draft.title, status = LBFeedbackStatus.Pending)
        return id
    }

    override suspend fun get(remoteId: String): LBFeedbackRemoteState {
        simulate()
        val remote = remotes[remoteId] ?: throw LBFeedbackError.NotFound()
        return LBFeedbackRemoteState(status = remote.status, title = remote.title)
    }

    override suspend fun update(remoteId: String, draft: LBFeedbackDraft, metadata: LBFeedbackMetadata) {
        simulate()
        remotes[remoteId]?.title = draft.title
    }

    override suspend fun delete(remoteId: String) {
        simulate()
        remotes.remove(remoteId)
    }

    override suspend fun attach(remoteId: String, attachment: LBFeedbackAttachment) {
        simulate()
    }

    fun triage() {
        remotes.values.forEach {
            it.status = when (it.status) {
                LBFeedbackStatus.Pending -> LBFeedbackStatus.Accepted
                LBFeedbackStatus.Accepted -> LBFeedbackStatus.Declined
                LBFeedbackStatus.Declined -> LBFeedbackStatus.Snoozed
                LBFeedbackStatus.Snoozed -> LBFeedbackStatus.Duplicate
                LBFeedbackStatus.Duplicate -> LBFeedbackStatus.Pending
            }
        }
    }

    private suspend fun simulate() {
        delay(SimulatedDelayMillis)
        if (simulateNetworkError) throw LBFeedbackError.Network()
    }

    private companion object {
        const val SimulatedDelayMillis: Long = 600L
    }
}

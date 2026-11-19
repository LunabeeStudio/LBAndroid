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

package studio.lunabee.feedback.test

import studio.lunabee.feedback.core.client.LBFeedbackClient
import studio.lunabee.feedback.core.metadata.LBFeedbackMetadataProvider
import studio.lunabee.feedback.core.model.LBFeedbackAttachment
import studio.lunabee.feedback.core.model.LBFeedbackDraft
import studio.lunabee.feedback.core.model.LBFeedbackItem
import studio.lunabee.feedback.core.model.LBFeedbackMetadata
import studio.lunabee.feedback.core.model.LBFeedbackRemoteState
import studio.lunabee.feedback.core.model.LBFeedbackStatus
import studio.lunabee.feedback.core.model.LBFeedbackType
import studio.lunabee.feedback.core.store.LBFeedbackStore
import studio.lunabee.feedback.core.transport.LBFeedbackError
import studio.lunabee.feedback.core.transport.LBFeedbackTransport
import kotlin.time.Instant

/** In-memory [LBFeedbackTransport] recording its calls, for tests. */
class FakeLBFeedbackTransport : LBFeedbackTransport {
    var sendError: LBFeedbackError? = null
    var deleteError: LBFeedbackError? = null
    var failAttachmentNames: Set<String> = emptySet()
    var remoteStatuses: Map<String, LBFeedbackStatus> = emptyMap()
    val calls: MutableList<String> = mutableListOf()

    override suspend fun send(draft: LBFeedbackDraft, metadata: LBFeedbackMetadata): String {
        calls += "send"
        sendError?.let { throw it }
        return "remote-1"
    }

    override suspend fun get(remoteId: String): LBFeedbackRemoteState {
        calls += "get:$remoteId"
        val status = remoteStatuses[remoteId] ?: throw LBFeedbackError.Network()
        return LBFeedbackRemoteState(status = status, title = "")
    }

    override suspend fun update(remoteId: String, draft: LBFeedbackDraft, metadata: LBFeedbackMetadata) {
        calls += "update:$remoteId"
    }

    override suspend fun delete(remoteId: String) {
        calls += "delete:$remoteId"
        deleteError?.let { throw it }
    }

    override suspend fun attach(remoteId: String, attachment: LBFeedbackAttachment) {
        calls += "attach:${attachment.name}"
        if (attachment.name in failAttachmentNames) throw LBFeedbackError.Network()
    }
}

/** A metadata fixture. */
fun fakeMetadata(extras: Map<String, String> = emptyMap()): LBFeedbackMetadata = LBFeedbackMetadata(
    appVersionName = "1.2.3",
    appVersionCode = 42L,
    buildVariant = "devBeta",
    deviceManufacturer = "Google",
    deviceModel = "Pixel 9",
    osVersion = "Android 16",
    locale = "fr-FR",
    libraryVersion = "1.0.0",
    sentAt = Instant.fromEpochMilliseconds(0L),
    extras = extras,
)

/** A sent item fixture. */
fun fakeItem(
    remoteId: String = "remote-1",
    status: LBFeedbackStatus = LBFeedbackStatus.Pending,
): LBFeedbackItem = LBFeedbackItem(
    remoteId = remoteId,
    type = LBFeedbackType.Bug,
    title = "Crash",
    body = "Body",
    reporter = null,
    metadata = fakeMetadata(),
    createdAt = Instant.fromEpochMilliseconds(0L),
    status = status,
)

/** A text attachment fixture of [size] bytes. */
fun fakeFile(name: String, size: Int = 2): LBFeedbackAttachment =
    LBFeedbackAttachment(bytes = ByteArray(size), name = name, mimeType = "text/plain")

/** A client on [transport] and [store] whose metadata is [fakeMetadata]. */
fun fakeClient(transport: LBFeedbackTransport, store: LBFeedbackStore): LBFeedbackClient =
    LBFeedbackClient(transport = transport, store = store, metadataProvider = LBFeedbackMetadataProvider { fakeMetadata() })

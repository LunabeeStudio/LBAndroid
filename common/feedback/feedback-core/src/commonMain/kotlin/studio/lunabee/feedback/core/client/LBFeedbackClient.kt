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

package studio.lunabee.feedback.core.client

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import studio.lunabee.feedback.core.config.LBFeedbackConfig
import studio.lunabee.feedback.core.metadata.LBFeedbackDefaultMetadataProvider
import studio.lunabee.feedback.core.metadata.LBFeedbackMetadataProvider
import studio.lunabee.feedback.core.model.LBFeedbackAttachment
import studio.lunabee.feedback.core.model.LBFeedbackDraft
import studio.lunabee.feedback.core.model.LBFeedbackItem
import studio.lunabee.feedback.core.model.LBFeedbackStatus
import studio.lunabee.feedback.core.store.LBFeedbackStore
import studio.lunabee.feedback.core.transport.LBFeedbackError
import studio.lunabee.feedback.core.transport.LBFeedbackTransport
import kotlin.time.Clock

/**
 * Headless feedback API, for apps that build their own UI. It holds the business rules once: the built-in screens of
 * `feedback-ui` use it too.
 *
 * Functions throw an [LBFeedbackError] when the remote call fails, or when the item is unknown ([LBFeedbackError.NotFound])
 * or no longer pending ([LBFeedbackError.NotEditable]).
 *
 * @param transport where feedback is sent
 * @param store local memory of the sent items
 * @param metadataProvider source of the metadata added to each feedback
 */
class LBFeedbackClient(
    private val transport: LBFeedbackTransport,
    private val store: LBFeedbackStore,
    private val metadataProvider: LBFeedbackMetadataProvider,
    private val clock: Clock = Clock.System,
) {
    /**
     * Same as the main constructor, with the standard metadata built from [config].
     */
    constructor(
        config: LBFeedbackConfig,
        transport: LBFeedbackTransport,
        store: LBFeedbackStore,
    ) : this(transport = transport, store = store, metadataProvider = LBFeedbackDefaultMetadataProvider(config))

    /** Sent items, newest first, with their last known status. */
    val items: Flow<List<LBFeedbackItem>> = store.items

    /** Name remembered for the next feedback, `null` when none. */
    val reporterName: Flow<String?> = store.reporterName

    /**
     * Whether [item] can still be edited or deleted: only while it is pending.
     */
    fun canEdit(item: LBFeedbackItem): Boolean = item.status == LBFeedbackStatus.Pending

    /**
     * Remembers [name] for the next feedback, or forgets it when `null`.
     */
    suspend fun setReporterName(name: String?) {
        store.setReporterName(name)
    }

    /**
     * Creates the feedback, remembers it, then uploads the attachments of [draft] one after the other.
     *
     * The item is kept once created. When an upload fails, the next ones are not tried and the result is
     * [LBFeedbackSendResult.PartiallySent] with the files left to upload.
     *
     * @throws LBFeedbackError.TooLarge before any call, when an attachment is over [LBFeedbackAttachment.MaxBytes]
     * @throws LBFeedbackError when the item could not be created, nothing is stored then
     */
    suspend fun send(draft: LBFeedbackDraft): LBFeedbackSendResult {
        draft.attachments.forEach(::checkSize)
        val metadata = metadataProvider.collect()
        val remoteId = transport.send(draft, metadata)
        val item = LBFeedbackItem(
            remoteId = remoteId,
            type = draft.type,
            title = draft.title,
            body = draft.body,
            reporter = draft.reporter,
            metadata = metadata,
            createdAt = clock.now(),
            status = LBFeedbackStatus.Pending,
        )
        store.add(item)
        store.setReporterName(draft.reporter)
        return upload(item, draft.attachments)
    }

    /**
     * Uploads [attachments] to the item [remoteId], for example the ones of a [LBFeedbackSendResult.PartiallySent].
     *
     * @throws LBFeedbackError.TooLarge before any call, when an attachment is over [LBFeedbackAttachment.MaxBytes]
     * @throws LBFeedbackError.NotFound when the item is not in the store
     */
    suspend fun retryAttachments(remoteId: String, attachments: List<LBFeedbackAttachment>): LBFeedbackSendResult {
        attachments.forEach(::checkSize)
        return upload(find(remoteId), attachments)
    }

    /**
     * Refreshes the status of every stored item. An item whose status cannot be read keeps its last known one.
     */
    suspend fun refreshStatuses() {
        for (item in store.items.first()) {
            val remote = try {
                transport.get(item.remoteId)
            } catch (_: LBFeedbackError) {
                continue
            }
            store.update(item.remoteId) { it.copy(status = remote.status) }
        }
    }

    /**
     * Replaces the type, title, body and reporter of the pending item [remoteId] with those of [draft]. Attachments of
     * [draft] are ignored.
     *
     * @throws LBFeedbackError.NotEditable when the item is not pending
     * @throws LBFeedbackError.NotFound when the item is not in the store
     */
    suspend fun update(remoteId: String, draft: LBFeedbackDraft) {
        val item = findEditable(remoteId)
        transport.update(remoteId, draft, item.metadata)
        store.update(remoteId) { it.copy(type = draft.type, title = draft.title, body = draft.body, reporter = draft.reporter) }
        store.setReporterName(draft.reporter)
    }

    /**
     * Deletes the pending item [remoteId], remotely then locally. When the remote item is already gone, it is removed
     * locally anyway.
     *
     * @throws LBFeedbackError.NotEditable when the item is not pending
     * @throws LBFeedbackError.NotFound when the item is not in the store
     */
    suspend fun delete(remoteId: String) {
        findEditable(remoteId)
        try {
            transport.delete(remoteId)
        } catch (e: LBFeedbackError) {
            if (e !is LBFeedbackError.NotFound) throw e
        }
        store.remove(remoteId)
    }

    private suspend fun upload(item: LBFeedbackItem, attachments: List<LBFeedbackAttachment>): LBFeedbackSendResult {
        attachments.forEachIndexed { index, attachment ->
            try {
                transport.attach(item.remoteId, attachment)
            } catch (e: LBFeedbackError) {
                return LBFeedbackSendResult.PartiallySent(item, attachments.drop(index), e)
            }
        }
        return LBFeedbackSendResult.Sent(item)
    }

    private suspend fun find(remoteId: String): LBFeedbackItem =
        store.items.first().firstOrNull { it.remoteId == remoteId } ?: throw LBFeedbackError.NotFound()

    private suspend fun findEditable(remoteId: String): LBFeedbackItem {
        val item = find(remoteId)
        if (!canEdit(item)) throw LBFeedbackError.NotEditable()
        return item
    }

    private fun checkSize(attachment: LBFeedbackAttachment) {
        if (attachment.bytes.size > LBFeedbackAttachment.MaxBytes) throw LBFeedbackError.TooLarge()
    }
}

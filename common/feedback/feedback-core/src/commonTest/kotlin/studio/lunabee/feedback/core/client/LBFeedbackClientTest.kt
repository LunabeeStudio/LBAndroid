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

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import studio.lunabee.feedback.core.metadata.LBFeedbackMetadataProvider
import studio.lunabee.feedback.core.model.LBFeedbackAttachment
import studio.lunabee.feedback.core.model.LBFeedbackDraft
import studio.lunabee.feedback.core.model.LBFeedbackStatus
import studio.lunabee.feedback.core.model.LBFeedbackType
import studio.lunabee.feedback.core.transport.LBFeedbackError
import studio.lunabee.feedback.test.FakeLBFeedbackStore
import studio.lunabee.feedback.test.FakeLBFeedbackTransport
import studio.lunabee.feedback.test.fakeFile
import studio.lunabee.feedback.test.fakeItem
import studio.lunabee.feedback.test.fakeMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LBFeedbackClientTest {
    private val transport = FakeLBFeedbackTransport()
    private val store = FakeLBFeedbackStore()
    private val client = LBFeedbackClient(
        transport = transport,
        store = store,
        metadataProvider = LBFeedbackMetadataProvider { fakeMetadata() },
    )

    private fun draft(
        reporter: String? = null,
        attachments: List<LBFeedbackAttachment> = emptyList(),
    ): LBFeedbackDraft = LBFeedbackDraft(
        type = LBFeedbackType.Bug,
        title = "Crash",
        body = "Body",
        reporter = reporter,
        attachments = attachments,
    )

    @Test
    fun sendStoresItemAndRemembersReporter() = runTest {
        val result = client.send(draft(reporter = "Ada"))

        assertIs<LBFeedbackSendResult.Sent>(result)
        assertEquals("remote-1", store.items.first().single().remoteId)
        assertEquals("Ada", store.reporterName.first())
        assertEquals(listOf("send"), transport.calls)
    }

    @Test
    fun sendUploadsEveryAttachmentInOrder() = runTest {
        client.send(draft(attachments = listOf(fakeFile("a"), fakeFile("b"))))

        assertEquals(listOf("send", "attach:a", "attach:b"), transport.calls)
    }

    @Test
    fun sendFailureStoresNothing() = runTest {
        transport.sendError = LBFeedbackError.Network()

        assertFailsWith<LBFeedbackError.Network> { client.send(draft()) }

        assertTrue(store.items.first().isEmpty())
    }

    @Test
    fun oversizedAttachmentFailsBeforeAnyCall() = runTest {
        val big = fakeFile("big", LBFeedbackAttachment.MaxBytes + 1)

        assertFailsWith<LBFeedbackError.TooLarge> { client.send(draft(attachments = listOf(fakeFile("a"), big))) }

        assertTrue(transport.calls.isEmpty())
    }

    @Test
    fun partialFailureKeepsItemAndRetryUploadsOnlyFailedFiles() = runTest {
        transport.failAttachmentNames = setOf("b")
        val files = listOf(fakeFile("a"), fakeFile("b"), fakeFile("c"))

        val result = client.send(draft(attachments = files))

        val partial = assertIs<LBFeedbackSendResult.PartiallySent>(result)
        assertEquals(listOf("b", "c"), partial.failedAttachments.map { it.name })
        assertIs<LBFeedbackError.Network>(partial.error)
        assertEquals(1, store.items.first().size)

        transport.failAttachmentNames = emptySet()
        transport.calls.clear()
        val retry = client.retryAttachments("remote-1", partial.failedAttachments)

        assertIs<LBFeedbackSendResult.Sent>(retry)
        assertEquals(listOf("attach:b", "attach:c"), transport.calls)
    }

    @Test
    fun editedPendingItemIsUpdatedRemotelyAndLocally() = runTest {
        store.add(fakeItem())

        client.update("remote-1", draft(reporter = "Ada").let { LBFeedbackDraft(it.type, "New", it.body, it.reporter) })

        assertEquals("New", store.items.first().single().title)
        assertEquals(listOf("update:remote-1"), transport.calls)
    }

    @Test
    fun editAndDeleteRefusedWhenNotPending() = runTest {
        store.add(fakeItem(status = LBFeedbackStatus.Accepted))

        assertFailsWith<LBFeedbackError.NotEditable> { client.update("remote-1", draft()) }
        assertFailsWith<LBFeedbackError.NotEditable> { client.delete("remote-1") }

        assertTrue(transport.calls.isEmpty())
    }

    @Test
    fun canEditOnlyWhilePending() {
        assertTrue(client.canEdit(fakeItem(status = LBFeedbackStatus.Pending)))
        assertTrue(!client.canEdit(fakeItem(status = LBFeedbackStatus.Declined)))
    }

    @Test
    fun deleteRemovesItemRemotelyAndLocally() = runTest {
        store.add(fakeItem())

        client.delete("remote-1")

        assertTrue(store.items.first().isEmpty())
        assertEquals(listOf("delete:remote-1"), transport.calls)
    }

    @Test
    fun deleteNotFoundRemovesLocally() = runTest {
        store.add(fakeItem())
        transport.deleteError = LBFeedbackError.NotFound()

        client.delete("remote-1")

        assertTrue(store.items.first().isEmpty())
    }

    @Test
    fun deleteOtherErrorKeepsItem() = runTest {
        store.add(fakeItem())
        transport.deleteError = LBFeedbackError.Network()

        assertFailsWith<LBFeedbackError.Network> { client.delete("remote-1") }

        assertEquals(1, store.items.first().size)
    }

    @Test
    fun refreshUpdatesStatusAndKeepsLastKnownOnFailure() = runTest {
        store.add(fakeItem("b"))
        store.add(fakeItem("a"))
        transport.remoteStatuses = mapOf("a" to LBFeedbackStatus.Declined)

        client.refreshStatuses()

        val statuses = store.items.first().associate { it.remoteId to it.status }
        assertEquals(mapOf("a" to LBFeedbackStatus.Declined, "b" to LBFeedbackStatus.Pending), statuses)
    }

    @Test
    fun deleteOfUnknownItemIsNotFound() = runTest {
        assertFailsWith<LBFeedbackError.NotFound> { client.delete("nope") }
    }

    @Test
    fun refreshKeepsConcurrentEditOfOtherFields() = runTest {
        store.add(fakeItem())
        transport.remoteStatuses = mapOf("remote-1" to LBFeedbackStatus.Accepted)

        store.update("remote-1") { it.copy(title = "Edited") }
        client.refreshStatuses()

        val item = store.items.first().single()
        assertEquals("Edited", item.title)
        assertEquals(LBFeedbackStatus.Accepted, item.status)
    }

    @Test
    fun retryOnUnknownItemIsNotFound() = runTest {
        assertFailsWith<LBFeedbackError.NotFound> { client.retryAttachments("nope", listOf(fakeFile("a"))) }
    }
}

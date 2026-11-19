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

package studio.lunabee.feedback.ui.list

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import studio.lunabee.feedback.core.model.LBFeedbackStatus
import studio.lunabee.feedback.core.transport.LBFeedbackError
import studio.lunabee.feedback.core.store.InMemoryLBFeedbackStore
import studio.lunabee.feedback.test.FakeLBFeedbackTransport
import studio.lunabee.feedback.test.fakeClient
import studio.lunabee.feedback.test.fakeItem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LBFeedbackListViewModelTest {
    private val transport = FakeLBFeedbackTransport()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun editableOnlyWhilePending() = runTest {
        val store = InMemoryLBFeedbackStore(
            listOf(fakeItem("a", LBFeedbackStatus.Pending), fakeItem("b", LBFeedbackStatus.Accepted)),
        )
        transport.remoteStatuses = mapOf("a" to LBFeedbackStatus.Pending, "b" to LBFeedbackStatus.Accepted)

        val rows = LBFeedbackListViewModel(fakeClient(transport, store)).uiState.value.rows.associate { it.remoteId to it.isEditable }

        assertEquals(mapOf("a" to true, "b" to false), rows)
    }

    @Test
    fun refreshUpdatesStatusAndKeepsLastKnownOnFailure() = runTest {
        val store = InMemoryLBFeedbackStore(listOf(fakeItem("a"), fakeItem("b")))
        transport.remoteStatuses = mapOf("a" to LBFeedbackStatus.Declined)

        val viewModel = LBFeedbackListViewModel(fakeClient(transport, store))

        val statuses = viewModel.uiState.value.rows.associate { it.remoteId to it.status }
        assertEquals(mapOf("a" to LBFeedbackStatus.Declined, "b" to LBFeedbackStatus.Pending), statuses)
    }

    @Test
    fun deleteRemovesPendingItem() = runTest {
        val store = InMemoryLBFeedbackStore(listOf(fakeItem("a")))
        transport.remoteStatuses = mapOf("a" to LBFeedbackStatus.Pending)
        val viewModel = LBFeedbackListViewModel(fakeClient(transport, store))

        viewModel.delete("a")

        assertTrue(store.items.first().isEmpty())
        assertTrue("delete:a" in transport.calls)
    }

    @Test
    fun deleteNotFoundRemovesLocally() = runTest {
        val store = InMemoryLBFeedbackStore(listOf(fakeItem("a")))
        transport.remoteStatuses = mapOf("a" to LBFeedbackStatus.Pending)
        transport.deleteError = LBFeedbackError.NotFound()
        val viewModel = LBFeedbackListViewModel(fakeClient(transport, store))

        viewModel.delete("a")

        assertTrue(store.items.first().isEmpty())
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun deleteOtherErrorKeepsItemAndExposesError() = runTest {
        val store = InMemoryLBFeedbackStore(listOf(fakeItem("a")))
        transport.remoteStatuses = mapOf("a" to LBFeedbackStatus.Pending)
        transport.deleteError = LBFeedbackError.Network()
        val viewModel = LBFeedbackListViewModel(fakeClient(transport, store))

        viewModel.delete("a")

        assertEquals(1, store.items.first().size)
        assertIs<LBFeedbackError.Network>(viewModel.uiState.value.error)
    }

    @Test
    fun deleteOfItemTriagedMeanwhileExposesNotEditable() = runTest {
        val store = InMemoryLBFeedbackStore(listOf(fakeItem("a")))
        transport.remoteStatuses = mapOf("a" to LBFeedbackStatus.Pending)
        val viewModel = LBFeedbackListViewModel(fakeClient(transport, store))
        store.update("a") { it.copy(status = LBFeedbackStatus.Accepted) }

        viewModel.delete("a")

        assertFalse("delete:a" in transport.calls)
        assertEquals(1, store.items.first().size)
    }

    @Test
    fun deleteIgnoredWhenNotPending() = runTest {
        val store = InMemoryLBFeedbackStore(listOf(fakeItem("b", LBFeedbackStatus.Accepted)))
        transport.remoteStatuses = mapOf("b" to LBFeedbackStatus.Accepted)
        val viewModel = LBFeedbackListViewModel(fakeClient(transport, store))

        viewModel.delete("b")

        assertFalse("delete:b" in transport.calls)
    }
}

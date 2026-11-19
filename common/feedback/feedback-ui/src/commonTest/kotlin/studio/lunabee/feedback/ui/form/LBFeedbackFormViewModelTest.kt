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

package studio.lunabee.feedback.ui.form

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import studio.lunabee.feedback.core.model.LBFeedbackAttachment
import studio.lunabee.feedback.core.transport.LBFeedbackError
import studio.lunabee.feedback.test.FakeLBFeedbackStore
import studio.lunabee.feedback.test.FakeLBFeedbackTransport
import studio.lunabee.feedback.test.fakeClient
import studio.lunabee.feedback.test.fakeFile
import studio.lunabee.feedback.test.fakeItem
import studio.lunabee.feedback.ui.LBFeedbackPending
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LBFeedbackFormViewModelTest {
    private val transport = FakeLBFeedbackTransport()
    private val pending = LBFeedbackPending()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel(): LBFeedbackFormViewModel = LBFeedbackFormViewModel(
        client = fakeClient(transport, FakeLBFeedbackStore()),
        pending = pending,
        editRemoteId = null,
    )

    private fun LBFeedbackFormViewModel.fill() {
        onTitleChange("Crash")
        onBodyChange("Body")
    }

    @Test
    fun sendSuccessEndsInDoneAndClearsPending() = runTest {
        pending.files.value = listOf(fakeFile("log.txt"))
        val viewModel = newViewModel()
        viewModel.fill()

        viewModel.submit()

        assertEquals(LBFeedbackFormPhase.Done, viewModel.uiState.value.phase)
        assertTrue(pending.files.value.isEmpty())
        assertEquals(listOf("send", "attach:log.txt"), transport.calls)
    }

    @Test
    fun screenshotIsSentOnlyWhenSwitchIsOn() = runTest {
        pending.screenshot.value = fakeFile(LBFeedbackAttachment.screenshotName())
        val viewModel = newViewModel()
        viewModel.fill()

        viewModel.submit()

        assertEquals(listOf("send"), transport.calls)
    }

    @Test
    fun errorKeepsDraftAndPendingAndExposesError() = runTest {
        pending.files.value = listOf(fakeFile("log.txt"))
        transport.sendError = LBFeedbackError.Network()
        val viewModel = newViewModel()
        viewModel.fill()

        viewModel.submit()

        val state = viewModel.uiState.value
        assertIs<LBFeedbackError.Network>(assertIs<LBFeedbackFormPhase.Failed>(state.phase).error)
        assertEquals("Crash", state.title)
        assertEquals(1, pending.files.value.size)
    }

    @Test
    fun partialFailureExposesFailedFilesAndRetryUploadsOnlyThem() = runTest {
        pending.files.value = listOf(fakeFile("a"), fakeFile("b"))
        transport.failAttachmentNames = setOf("b")
        val viewModel = newViewModel()
        viewModel.fill()

        viewModel.submit()

        val phase = assertIs<LBFeedbackFormPhase.AttachmentsFailed>(viewModel.uiState.value.phase)
        assertEquals(listOf("b"), phase.failed.map { it.name })

        transport.failAttachmentNames = emptySet()
        viewModel.submit()

        assertEquals(LBFeedbackFormPhase.Done, viewModel.uiState.value.phase)
        assertEquals(listOf("send", "attach:a", "attach:b", "attach:b"), transport.calls)
    }

    @Test
    fun doneClearsOnlyTheSentFiles() = runTest {
        val sentFile = fakeFile("a")
        pending.files.value = listOf(sentFile)
        val viewModel = newViewModel()
        viewModel.fill()
        val late = fakeFile("late")

        viewModel.submit()
        pending.files.value = pending.files.value + late

        assertEquals(LBFeedbackFormPhase.Done, viewModel.uiState.value.phase)
        assertEquals(listOf("late"), pending.files.value.map { it.name })
    }

    @Test
    fun leaveClearsPendingOnlyForNewFeedback() = runTest {
        pending.files.value = listOf(fakeFile("a"))
        val creating = newViewModel()
        val store = FakeLBFeedbackStore(listOf(fakeItem()))
        val editing = LBFeedbackFormViewModel(fakeClient(transport, store), pending, editRemoteId = "remote-1")

        editing.leave()
        assertEquals(1, pending.files.value.size)

        creating.leave()
        assertTrue(pending.files.value.isEmpty())
    }

    @Test
    fun removeFileDropsItFromState() = runTest {
        pending.files.value = listOf(fakeFile("a"), fakeFile("b"))
        val viewModel = newViewModel()

        viewModel.removeFile("a")

        assertEquals(listOf("b"), viewModel.uiState.value.files.map { it.name })
    }

    @Test
    fun attachSwitchStaysOffWithoutScreenshot() = runTest {
        val viewModel = newViewModel()

        viewModel.onAttachScreenshotChange(true)

        assertNull(viewModel.uiState.value.screenshot)
        assertEquals(false, viewModel.uiState.value.attachScreenshot)
    }
}

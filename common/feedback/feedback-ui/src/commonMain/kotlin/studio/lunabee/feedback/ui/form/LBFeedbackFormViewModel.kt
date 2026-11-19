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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.lunabee.feedback.core.client.LBFeedbackClient
import studio.lunabee.feedback.core.client.LBFeedbackSendResult
import studio.lunabee.feedback.core.model.LBFeedbackAttachment
import studio.lunabee.feedback.core.model.LBFeedbackDraft
import studio.lunabee.feedback.core.model.LBFeedbackType
import studio.lunabee.feedback.core.transport.LBFeedbackError
import studio.lunabee.feedback.ui.LBFeedbackPending

internal class LBFeedbackFormViewModel(
    private val client: LBFeedbackClient,
    private val pending: LBFeedbackPending,
    private val editRemoteId: String?,
) : ViewModel() {
    private var sent: List<LBFeedbackAttachment> = emptyList()

    private val _uiState = MutableStateFlow(LBFeedbackFormUiState(isEdit = editRemoteId != null))
    val uiState: StateFlow<LBFeedbackFormUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            pending.screenshot.collect { current ->
                _uiState.update {
                    it.copy(screenshot = current, attachScreenshot = it.attachScreenshot && current != null)
                }
            }
        }
        viewModelScope.launch {
            pending.files.collect { current -> _uiState.update { it.copy(files = current) } }
        }
    }

    fun onTypeChange(type: LBFeedbackType) = _uiState.update { it.copy(type = type) }

    fun onTitleChange(title: String) = _uiState.update { it.copy(title = title) }

    fun onBodyChange(body: String) = _uiState.update { it.copy(body = body) }

    fun onReporterChange(reporter: String) = _uiState.update { it.copy(reporter = reporter) }

    fun onAttachScreenshotChange(attach: Boolean) = _uiState.update {
        it.copy(attachScreenshot = attach && pending.screenshot.value != null)
    }

    fun removeScreenshot() {
        pending.screenshot.value = null
    }

    fun removeFile(name: String) = pending.files.update { files -> files.filterNot { it.name == name } }

    fun leave() {
        if (editRemoteId == null) pending.clear()
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        val phase = state.phase
        _uiState.update { it.copy(phase = LBFeedbackFormPhase.Sending) }
        viewModelScope.launch {
            val next = try {
                run(phase, state)
            } catch (e: LBFeedbackError) {
                failureOf(phase, e)
            }
            if (next == LBFeedbackFormPhase.Done) removeSent()
            _uiState.update { it.copy(phase = next) }
        }
    }

    private fun fail(error: LBFeedbackError) {
        _uiState.update { it.copy(phase = LBFeedbackFormPhase.Failed(error)) }
    }

    private fun removeSent() {
        pending.files.update { files -> files.filterNot { file -> sent.any { it === file } } }
        pending.screenshot.update { current -> current.takeUnless { screenshot -> sent.any { it === screenshot } } }
        sent = emptyList()
    }

    private suspend fun load() {
        if (editRemoteId == null) {
            client.reporterName.first()?.let { name ->
                _uiState.update { if (it.reporter.isBlank()) it.copy(reporter = name) else it }
            }
            return
        }
        val item = client.items.first().firstOrNull { it.remoteId == editRemoteId }
            ?: return fail(LBFeedbackError.NotFound())
        if (!client.canEdit(item)) return fail(LBFeedbackError.NotEditable())
        _uiState.update {
            it.copy(type = item.type, title = item.title, body = item.body, reporter = item.reporter.orEmpty())
        }
    }

    private suspend fun run(phase: LBFeedbackFormPhase, state: LBFeedbackFormUiState): LBFeedbackFormPhase = when {
        phase is LBFeedbackFormPhase.AttachmentsFailed ->
            phaseOf(client.retryAttachments(phase.remoteId, phase.failed))

        editRemoteId != null -> {
            client.update(editRemoteId, draftOf(state, emptyList()))
            LBFeedbackFormPhase.Done
        }

        else -> {
            val attachments = listOfNotNull(state.screenshot?.takeIf { state.attachScreenshot }) + state.files
            sent = attachments
            phaseOf(client.send(draftOf(state, attachments)))
        }
    }

    private fun phaseOf(result: LBFeedbackSendResult): LBFeedbackFormPhase = when (result) {
        is LBFeedbackSendResult.Sent -> LBFeedbackFormPhase.Done

        is LBFeedbackSendResult.PartiallySent -> LBFeedbackFormPhase.AttachmentsFailed(
            remoteId = result.item.remoteId,
            failed = result.failedAttachments,
            error = result.error,
        )
    }

    private fun failureOf(phase: LBFeedbackFormPhase, error: LBFeedbackError): LBFeedbackFormPhase =
        if (phase is LBFeedbackFormPhase.AttachmentsFailed) {
            phase.copy(error = error)
        } else {
            LBFeedbackFormPhase.Failed(error)
        }

    private fun draftOf(
        state: LBFeedbackFormUiState,
        attachments: List<LBFeedbackAttachment>,
    ): LBFeedbackDraft =
        LBFeedbackDraft(
            type = state.type,
            title = state.title.trim(),
            body = state.body.trim(),
            reporter = state.reporter.trim().ifBlank { null },
            attachments = attachments,
        )
}

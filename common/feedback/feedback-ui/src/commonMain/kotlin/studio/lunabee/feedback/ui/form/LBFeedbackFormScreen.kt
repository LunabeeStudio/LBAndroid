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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import studio.lunabee.feedback.core.model.LBFeedbackAttachment
import studio.lunabee.feedback.core.model.LBFeedbackType
import studio.lunabee.feedback.ui.LBFeedback
import studio.lunabee.feedback.ui.LBFeedbackBackHandler
import studio.lunabee.feedback.ui.LBFeedbackTestTags
import studio.lunabee.feedback.ui.error.feedbackErrorMessage
import studio.lunabee.feedback.ui.res.Res
import studio.lunabee.feedback.ui.res.feedback_attach_screenshot
import studio.lunabee.feedback.ui.res.feedback_attachments
import studio.lunabee.feedback.ui.res.feedback_close
import studio.lunabee.feedback.ui.res.feedback_field_body
import studio.lunabee.feedback.ui.res.feedback_field_reporter
import studio.lunabee.feedback.ui.res.feedback_field_title
import studio.lunabee.feedback.ui.res.feedback_form_edit_title
import studio.lunabee.feedback.ui.res.feedback_form_title
import studio.lunabee.feedback.ui.res.feedback_list_title
import studio.lunabee.feedback.ui.res.feedback_pick_gallery
import studio.lunabee.feedback.ui.res.feedback_retry
import studio.lunabee.feedback.ui.res.feedback_retry_attachments
import studio.lunabee.feedback.ui.res.feedback_save
import studio.lunabee.feedback.ui.res.feedback_attachments_failed
import studio.lunabee.feedback.ui.res.feedback_screenshot_preview
import studio.lunabee.feedback.ui.res.feedback_screenshot_redact
import studio.lunabee.feedback.ui.res.feedback_screenshot_remove
import studio.lunabee.feedback.ui.res.feedback_send
import studio.lunabee.feedback.ui.res.feedback_sending
import studio.lunabee.feedback.ui.res.feedback_type_bug
import studio.lunabee.feedback.ui.res.feedback_type_idea
import studio.lunabee.feedback.ui.res.feedback_type_other
import studio.lunabee.feedback.ui.theme.LBFeedbackTopBar

/**
 * Form to send a feedback, or to edit a pending one when [editRemoteId] is set. Inherits the app's `MaterialTheme`.
 *
 * @param onBackClick called when the user leaves the form without sending
 * @param onRedactClick called to open [studio.lunabee.feedback.ui.redact.LBFeedbackRedactScreen]
 * @param onFinished called once the feedback is sent (or saved), or the user closes the form after a partial failure
 * @param editRemoteId remote id of the item to edit, `null` to write a new feedback
 * @param onListClick when set, a "My feedback" action is shown in the top bar and calls it
 */
@Composable
fun LBFeedbackFormScreen(
    onBackClick: () -> Unit,
    onRedactClick: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    editRemoteId: String? = null,
    onListClick: (() -> Unit)? = null,
) {
    val graph = LBFeedback.graph()
    val viewModel: LBFeedbackFormViewModel = viewModel {
        LBFeedbackFormViewModel(client = graph.client, pending = graph.pending, editRemoteId = editRemoteId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickImage = rememberLBFeedbackImagePicker { viewModel.onAttachScreenshotChange(true) }
    val isDone = state.phase == LBFeedbackFormPhase.Done
    LaunchedEffect(isDone) { if (isDone) onFinished() }
    val isSending = state.phase == LBFeedbackFormPhase.Sending
    val currentOnBackClick by rememberUpdatedState(onBackClick)
    val currentOnFinished by rememberUpdatedState(onFinished)
    val leaveAndBack = remember(viewModel) {
        {
            viewModel.leave()
            currentOnBackClick()
        }
    }
    val leaveAndFinish = remember(viewModel) {
        {
            viewModel.leave()
            currentOnFinished()
        }
    }
    LBFeedbackBackHandler(enabled = true) { if (!isSending) leaveAndBack() }

    val actions = remember(viewModel, onRedactClick, onListClick, pickImage, state.isEdit) {
        LBFeedbackFormActions(
            onBackClick = leaveAndBack,
            onTypeChange = viewModel::onTypeChange,
            onTitleChange = viewModel::onTitleChange,
            onBodyChange = viewModel::onBodyChange,
            onReporterChange = viewModel::onReporterChange,
            onAttachScreenshotChange = viewModel::onAttachScreenshotChange,
            onRedactClick = onRedactClick,
            onRemoveScreenshotClick = viewModel::removeScreenshot,
            onRemoveFileClick = viewModel::removeFile,
            onSubmitClick = viewModel::submit,
            onPickImage = pickImage,
            onListClick = onListClick.takeIf { !state.isEdit },
        )
    }
    LBFeedbackFormContent(
        state = state,
        actions = actions,
        onCloseClick = leaveAndFinish,
        modifier = modifier,
    )
}

@Composable
internal fun LBFeedbackFormContent(
    state: LBFeedbackFormUiState,
    actions: LBFeedbackFormActions,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = if (state.isEdit) Res.string.feedback_form_edit_title else Res.string.feedback_form_title
    Scaffold(
        modifier = modifier,
        topBar = {
            LBFeedbackTopBar(
                title = stringResource(title),
                onBackClick = actions.onBackClick,
                backEnabled = state.phase != LBFeedbackFormPhase.Sending,
                actions = {
                    actions.onListClick?.let {
                        TextButton(onClick = it, modifier = Modifier.testTag(LBFeedbackTestTags.ListButton)) {
                            Text(stringResource(Res.string.feedback_list_title))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val editable = state.phase is LBFeedbackFormPhase.Editing || state.phase is LBFeedbackFormPhase.Failed
            TypeSelector(selected = state.type, enabled = editable, onChange = actions.onTypeChange)
            OutlinedTextField(
                value = state.title,
                onValueChange = actions.onTitleChange,
                label = { Text(stringResource(Res.string.feedback_field_title)) },
                enabled = editable,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(LBFeedbackTestTags.TitleField),
            )
            OutlinedTextField(
                value = state.body,
                onValueChange = actions.onBodyChange,
                label = { Text(stringResource(Res.string.feedback_field_body)) },
                enabled = editable,
                minLines = 4,
                modifier = Modifier.fillMaxWidth().testTag(LBFeedbackTestTags.BodyField),
            )
            OutlinedTextField(
                value = state.reporter,
                onValueChange = actions.onReporterChange,
                label = { Text(stringResource(Res.string.feedback_field_reporter)) },
                enabled = editable,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(LBFeedbackTestTags.ReporterField),
            )
            if (!state.isEdit && (state.screenshot != null || actions.onPickImage != null)) {
                ScreenshotSection(
                    screenshot = state.screenshot,
                    attachScreenshot = state.attachScreenshot,
                    enabled = editable,
                    actions = actions,
                )
            }
            if (!state.isEdit && state.files.isNotEmpty()) {
                FilesSection(files = state.files, enabled = editable, onRemoveClick = actions.onRemoveFileClick)
            }
            FormStatus(phase = state.phase)
            FormButtons(
                phase = state.phase,
                canSubmit = state.canSubmit,
                isEdit = state.isEdit,
                onSubmitClick = actions.onSubmitClick,
                onCloseClick = onCloseClick,
            )
        }
    }
}

@Composable
private fun TypeSelector(
    selected: LBFeedbackType,
    enabled: Boolean,
    onChange: (LBFeedbackType) -> Unit,
) {
    val options: List<Triple<LBFeedbackType, StringResource, String>> = listOf(
        Triple(LBFeedbackType.Bug, Res.string.feedback_type_bug, LBFeedbackTestTags.TypeBug),
        Triple(LBFeedbackType.Idea, Res.string.feedback_type_idea, LBFeedbackTestTags.TypeIdea),
        Triple(LBFeedbackType.Other, Res.string.feedback_type_other, LBFeedbackTestTags.TypeOther),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (type, label, tag) ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onChange(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                enabled = enabled,
                modifier = Modifier.testTag(tag),
            ) {
                Text(stringResource(label))
            }
        }
    }
}

@Composable
private fun ScreenshotSection(
    screenshot: LBFeedbackAttachment?,
    attachScreenshot: Boolean,
    enabled: Boolean,
    actions: LBFeedbackFormActions,
) {
    actions.onPickImage?.let {
        TextButton(
            onClick = it,
            enabled = enabled,
            modifier = Modifier.testTag(LBFeedbackTestTags.PickImageButton),
        ) {
            Text(stringResource(Res.string.feedback_pick_gallery))
        }
    }
    if (screenshot == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(Res.string.feedback_attach_screenshot), style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = attachScreenshot,
            onCheckedChange = actions.onAttachScreenshotChange,
            enabled = enabled,
            modifier = Modifier.testTag(LBFeedbackTestTags.AttachToggle),
        )
    }
    if (attachScreenshot) {
        val imageData = LBFeedback.graph().imageData
        val bitmap by produceState<ImageBitmap?>(initialValue = null, screenshot) {
            value = imageData.decode(screenshot.bytes)
        }
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = stringResource(Res.string.feedback_screenshot_preview),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = actions.onRedactClick,
                enabled = enabled,
                modifier = Modifier.testTag(LBFeedbackTestTags.RedactButton),
            ) {
                Text(stringResource(Res.string.feedback_screenshot_redact))
            }
            TextButton(
                onClick = actions.onRemoveScreenshotClick,
                enabled = enabled,
                modifier = Modifier.testTag(LBFeedbackTestTags.RemoveScreenshotButton),
            ) {
                Text(stringResource(Res.string.feedback_screenshot_remove))
            }
        }
    }
}

@Composable
private fun FilesSection(
    files: List<LBFeedbackAttachment>,
    enabled: Boolean,
    onRemoveClick: (String) -> Unit,
) {
    Text(stringResource(Res.string.feedback_attachments), style = MaterialTheme.typography.titleSmall)
    files.forEach { file ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${file.name} · ${formatSize(file.bytes.size)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { onRemoveClick(file.name) },
                enabled = enabled,
                modifier = Modifier.testTag(LBFeedbackTestTags.removeAttachmentButton(file.name)),
            ) {
                Text(stringResource(Res.string.feedback_screenshot_remove))
            }
        }
    }
}

private fun formatSize(bytes: Int): String = when {
    bytes < BytesPerKilobyte -> "$bytes B"
    bytes < BytesPerKilobyte * BytesPerKilobyte -> "${bytes / BytesPerKilobyte} KB"
    else -> "${bytes / (BytesPerKilobyte * BytesPerKilobyte)} MB"
}

private const val BytesPerKilobyte: Int = 1024

@Composable
private fun FormStatus(phase: LBFeedbackFormPhase) {
    val message: String? = when (phase) {
        is LBFeedbackFormPhase.Failed -> feedbackErrorMessage(phase.error)

        is LBFeedbackFormPhase.AttachmentsFailed ->
            stringResource(Res.string.feedback_attachments_failed) + "\n" + feedbackErrorMessage(phase.error)

        LBFeedbackFormPhase.Sending -> stringResource(Res.string.feedback_sending)

        LBFeedbackFormPhase.Editing, LBFeedbackFormPhase.Done -> null
    }
    if (message != null) {
        val isError = phase is LBFeedbackFormPhase.Failed || phase is LBFeedbackFormPhase.AttachmentsFailed
        Text(
            text = message,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun FormButtons(
    phase: LBFeedbackFormPhase,
    canSubmit: Boolean,
    isEdit: Boolean,
    onSubmitClick: () -> Unit,
    onCloseClick: () -> Unit,
) {
    when (phase) {
        is LBFeedbackFormPhase.AttachmentsFailed -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSubmitClick,
                modifier = Modifier.testTag(LBFeedbackTestTags.RetryAttachmentsButton),
            ) {
                Text(stringResource(Res.string.feedback_retry_attachments))
            }
            TextButton(
                onClick = onCloseClick,
                modifier = Modifier.testTag(LBFeedbackTestTags.CloseButton),
            ) {
                Text(stringResource(Res.string.feedback_close))
            }
        }

        is LBFeedbackFormPhase.Failed -> Button(
            onClick = onSubmitClick,
            enabled = canSubmit,
            modifier = Modifier.testTag(LBFeedbackTestTags.RetryButton),
        ) {
            Text(stringResource(Res.string.feedback_retry))
        }

        else -> Button(
            onClick = onSubmitClick,
            enabled = canSubmit && phase is LBFeedbackFormPhase.Editing,
            modifier = Modifier.testTag(LBFeedbackTestTags.SendButton),
        ) {
            Text(stringResource(if (isEdit) Res.string.feedback_save else Res.string.feedback_send))
        }
    }
}

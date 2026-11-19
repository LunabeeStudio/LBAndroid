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

package studio.lunabee.feedback.ui.redact

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import studio.lunabee.feedback.ui.LBFeedback
import studio.lunabee.feedback.ui.LBFeedbackBackHandler
import studio.lunabee.feedback.ui.LBFeedbackTestTags
import studio.lunabee.feedback.ui.image.LBFeedbackRedactBox
import studio.lunabee.feedback.ui.res.Res
import studio.lunabee.feedback.ui.res.feedback_redact_done
import studio.lunabee.feedback.ui.res.feedback_redact_hint
import studio.lunabee.feedback.ui.res.feedback_redact_title
import studio.lunabee.feedback.ui.res.feedback_redact_undo
import studio.lunabee.feedback.ui.res.feedback_screenshot_preview
import studio.lunabee.feedback.ui.theme.LBFeedbackTopBar

/**
 * Shows the pending screenshot and lets the user cover areas with opaque black boxes by dragging. Done burns the boxes
 * into the image in memory. Does nothing visible when no screenshot is pending. Inherits the app's `MaterialTheme`.
 *
 * @param onBackClick called when the user leaves without applying
 * @param onDone called after the boxes were applied
 */
@Composable
fun LBFeedbackRedactScreen(
    onBackClick: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val graph = LBFeedback.graph()
    val viewModel: LBFeedbackRedactViewModel = viewModel {
        LBFeedbackRedactViewModel(screenshot = graph.pending.screenshot, imageData = graph.imageData)
    }
    val boxes by viewModel.boxes.collectAsStateWithLifecycle()
    val source = viewModel.source
    val bitmap by produceState<ImageBitmap?>(initialValue = null, source) {
        value = source?.let { graph.imageData.decode(it.bytes) }
    }
    val isApplying by viewModel.isApplying.collectAsStateWithLifecycle()
    val isDone by viewModel.isDone.collectAsStateWithLifecycle()
    LaunchedEffect(isDone) { if (isDone) onDone() }
    LBFeedbackBackHandler(enabled = isApplying) {}

    LBFeedbackRedactContent(
        bitmap = bitmap,
        boxes = boxes,
        isApplying = isApplying,
        onBackClick = onBackClick,
        onAddBox = viewModel::addBox,
        onUndoClick = viewModel::undo,
        onDoneClick = viewModel::applyBoxes,
        modifier = modifier,
    )
}

@Composable
internal fun LBFeedbackRedactContent(
    bitmap: ImageBitmap?,
    boxes: List<LBFeedbackRedactBox>,
    isApplying: Boolean,
    onBackClick: () -> Unit,
    onAddBox: (LBFeedbackRedactBox) -> Unit,
    onUndoClick: () -> Unit,
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    Scaffold(
        modifier = modifier,
        topBar = {
            LBFeedbackTopBar(
                title = stringResource(Res.string.feedback_redact_title),
                onBackClick = onBackClick,
                backEnabled = !isApplying,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(Res.string.feedback_redact_hint), style = MaterialTheme.typography.bodyMedium)
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (bitmap != null) {
                    Box(
                        modifier = Modifier
                            .aspectRatio(bitmap.width.toFloat() / bitmap.height)
                            .onSizeChanged { size = it }
                            .pointerInput(bitmap) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragStart = it
                                        dragEnd = it
                                    },
                                    onDrag = { change, _ -> dragEnd = change.position },
                                    onDragEnd = {
                                        dragStart?.let { start -> boxOf(start, dragEnd, size)?.let(onAddBox) }
                                        dragStart = null
                                    },
                                    onDragCancel = { dragStart = null },
                                )
                            }
                            .testTag(LBFeedbackTestTags.RedactCanvas),
                    ) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = stringResource(Res.string.feedback_screenshot_preview),
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            boxes.forEach { drawBox(it, this.size) }
                            dragStart?.let { start ->
                                boxOf(start, dragEnd, size)?.let { drawBox(it, this.size) }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onUndoClick,
                    enabled = boxes.isNotEmpty(),
                    modifier = Modifier.testTag(LBFeedbackTestTags.UndoButton),
                ) {
                    Text(stringResource(Res.string.feedback_redact_undo))
                }
                Button(
                    onClick = onDoneClick,
                    enabled = !isApplying,
                    modifier = Modifier.testTag(LBFeedbackTestTags.DoneButton),
                ) {
                    Text(stringResource(Res.string.feedback_redact_done))
                }
            }
        }
    }
}

private fun boxOf(start: Offset, end: Offset, size: IntSize): LBFeedbackRedactBox? {
    if (size.width == 0 || size.height == 0) return null
    val left = (minOf(start.x, end.x) / size.width).coerceIn(0f, 1f)
    val right = (maxOf(start.x, end.x) / size.width).coerceIn(0f, 1f)
    val top = (minOf(start.y, end.y) / size.height).coerceIn(0f, 1f)
    val bottom = (maxOf(start.y, end.y) / size.height).coerceIn(0f, 1f)
    return if (right - left < MinBoxFraction || bottom - top < MinBoxFraction) {
        null
    } else {
        LBFeedbackRedactBox(left = left, top = top, right = right, bottom = bottom)
    }
}

private fun DrawScope.drawBox(box: LBFeedbackRedactBox, area: Size) {
    drawRect(
        color = Color.Black,
        topLeft = Offset(box.left * area.width, box.top * area.height),
        size = Size((box.right - box.left) * area.width, (box.bottom - box.top) * area.height),
    )
}

private const val MinBoxFraction: Float = 0.01f

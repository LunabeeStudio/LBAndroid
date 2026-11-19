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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.lunabee.feedback.core.model.LBFeedbackAttachment
import studio.lunabee.feedback.ui.image.LBFeedbackImageDataSource
import studio.lunabee.feedback.ui.image.LBFeedbackRedactBox

internal class LBFeedbackRedactViewModel(
    private val screenshot: MutableStateFlow<LBFeedbackAttachment?>,
    private val imageData: LBFeedbackImageDataSource,
) : ViewModel() {
    private val _boxes = MutableStateFlow<List<LBFeedbackRedactBox>>(emptyList())
    val boxes: StateFlow<List<LBFeedbackRedactBox>> = _boxes.asStateFlow()

    private val _isApplying = MutableStateFlow(false)
    val isApplying: StateFlow<Boolean> = _isApplying.asStateFlow()

    private val _isDone = MutableStateFlow(false)
    val isDone: StateFlow<Boolean> = _isDone.asStateFlow()

    val source: LBFeedbackAttachment? = screenshot.value

    fun addBox(box: LBFeedbackRedactBox) = _boxes.update { it + box }

    fun undo() = _boxes.update { it.dropLast(1) }

    fun applyBoxes() {
        if (_isApplying.value) return
        _isApplying.value = true
        viewModelScope.launch {
            val current = screenshot.value
            val boxes = _boxes.value
            if (current != null && boxes.isNotEmpty()) {
                screenshot.value = try {
                    imageData.redact(current, boxes)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            _isDone.value = true
        }
    }
}

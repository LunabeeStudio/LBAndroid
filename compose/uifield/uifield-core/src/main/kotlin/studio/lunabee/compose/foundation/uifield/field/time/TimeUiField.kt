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

package studio.lunabee.compose.foundation.uifield.field.time

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.text.input.VisualTransformation
import studio.lunabee.compose.foundation.uifield.field.style.UiFieldStyleData
import studio.lunabee.compose.foundation.uifield.field.text.TextUiField

abstract class TimeUiField<T>(uiFieldStyleData: UiFieldStyleData) : TextUiField<T>(uiFieldStyleData) {

    @Composable
    override fun Composable(modifier: Modifier, uiFieldStyleData: UiFieldStyleData) {
        val collectedValue by mValue.collectAsState()
        val collectedError by error.collectAsState()
        // https://stackoverflow.com/a/70335041
        val interactionSource = if (options.isNotEmpty() && enabled && !readOnly) {
            remember { MutableInteractionSource() }.also { interactionSource ->
                LaunchedEffect(interactionSource) {
                    interactionSource.interactions.collect { interaction ->
                        if (interaction is PressInteraction.Release) {
                            options.firstOrNull()?.onClick()
                        }
                    }
                }
            }
        } else {
            null
        }
        uiFieldStyleData.ComposeTextField(
            value = formToDisplay(collectedValue),
            onValueChange = {},
            modifier = modifier
                .fillMaxWidth()
                .onFocusEvent {
                    if (it.hasFocus) {
                        dismissError()
                        hasBeenFocused = true
                    }
                },
            placeholder = placeholder,
            label = label,
            trailingIcon = if (options.isNotEmpty()) {
                { options.forEach { it.Composable(modifier = Modifier) } }
            } else {
                null
            },
            visualTransformation = VisualTransformation.None,
            keyboardOptions = KeyboardOptions.Default,
            keyboardActions = KeyboardActions.Default,
            maxLine = 1,
            readOnly = readOnly,
            enabled = enabled,
            error = collectedError,
            interactionSource = interactionSource,
        )
    }
}

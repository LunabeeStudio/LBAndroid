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

package studio.lunabee.compose.foundation.uifield.field.style

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import studio.lunabee.compose.core.LbcTextSpec
import studio.lunabee.compose.foundation.uifield.field.UiFieldError

class PasswordUiFieldDataDefault : PasswordUiFieldData {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun ComposeTextField(
        value: String,
        onValueChange: (String) -> Unit,
        modifier: Modifier,
        placeholder: LbcTextSpec?,
        label: LbcTextSpec?,
        trailingIcon: @Composable (() -> Unit)?,
        isValueVisible: Boolean,
        keyboardOptions: KeyboardOptions,
        onKeyboardActions: KeyboardActionHandler,
        maxLine: Int,
        readOnly: Boolean,
        error: UiFieldError?,
        interactionSource: MutableInteractionSource?,
    ) {
        val colors = OutlinedTextFieldDefaults.colors()
        val state = remember { TextFieldState(value) }
        val interactionSourceToUse = interactionSource ?: remember { MutableInteractionSource() }

        BasicSecureTextField(
            state = state,
            textObfuscationMode = if (isValueVisible) {
                TextObfuscationMode.Visible
            } else {
                TextObfuscationMode.RevealLastTyped
            },
            enabled = !readOnly,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.focusedTextColor),
            keyboardOptions = keyboardOptions,
            interactionSource = interactionSourceToUse,
            cursorBrush = SolidColor(
                if (error != null) {
                    colors.errorCursorColor
                } else {
                    colors.cursorColor
                },
            ),
            onKeyboardAction = onKeyboardActions,
            decorator = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = state.text.toString(),
                    visualTransformation = VisualTransformation.None,
                    innerTextField = innerTextField,
                    placeholder = placeholder?.let { { Text(text = placeholder.string) } },
                    label = label?.let { { Text(text = label.string) } },
                    leadingIcon = null,
                    trailingIcon = trailingIcon,
                    prefix = null,
                    suffix = null,
                    supportingText = if (error?.text != null) {
                        { Text(text = error.text.string) }
                    } else {
                        null
                    },
                    singleLine = maxLine == 1,
                    enabled = true,
                    isError = error != null,
                    interactionSource = interactionSourceToUse,
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = !readOnly,
                            isError = error != null,
                            interactionSource = interactionSourceToUse,
                        )
                    },
                )
            },
            modifier = modifier,
        )

        LaunchedEffect(state.text) {
            onValueChange(state.text.toString())
        }
    }
}

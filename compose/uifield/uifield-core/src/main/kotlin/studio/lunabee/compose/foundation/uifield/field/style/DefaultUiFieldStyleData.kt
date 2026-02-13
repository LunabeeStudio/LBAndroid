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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import studio.lunabee.compose.core.LbcTextSpec
import studio.lunabee.compose.foundation.uifield.field.UiFieldError

class DefaultUiFieldStyleData : UiFieldStyleData {
    @Composable
    override fun ComposeTextField(
        value: TextFieldValue,
        onValueChange: (TextFieldValue) -> Unit,
        modifier: Modifier,
        placeholder: LbcTextSpec?,
        label: LbcTextSpec?,
        trailingIcon: @Composable (() -> Unit)?,
        visualTransformation: VisualTransformation,
        keyboardOptions: KeyboardOptions,
        keyboardActions: KeyboardActions,
        maxLine: Int,
        readOnly: Boolean,
        enabled: Boolean,
        error: UiFieldError?,
        interactionSource: MutableInteractionSource?,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.animateContentSize(),
            placeholder = placeholder?.let { { Text(text = placeholder.string) } },
            label = label?.let { { Text(text = label.string) } },
            trailingIcon = trailingIcon?.let { { Row { trailingIcon() } } },
            maxLines = maxLine,
            readOnly = readOnly,
            enabled = enabled,
            visualTransformation = visualTransformation,
            isError = error != null,
            supportingText = if (error?.text != null) {
                { Text(text = error.text.string) }
            } else {
                null
            },
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            singleLine = maxLine == 1,
        )
    }
}

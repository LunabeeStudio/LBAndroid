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

package studio.lunabee.compose.foundation.uifield.field.text

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import studio.lunabee.compose.core.LbcTextSpec
import studio.lunabee.compose.foundation.uifield.UiField
import studio.lunabee.compose.foundation.uifield.UiFieldOption
import studio.lunabee.compose.foundation.uifield.field.UiFieldError
import studio.lunabee.compose.foundation.uifield.field.style.PasswordUiFieldData
import studio.lunabee.compose.foundation.uifield.field.style.PasswordUiFieldDataDefault
import studio.lunabee.compose.foundation.uifield.field.text.option.password.PasswordVisibilityFieldOption
import studio.lunabee.compose.foundation.uifield.field.text.option.password.PasswordVisibilityOptionData
import studio.lunabee.compose.foundation.uifield.field.text.option.password.PasswordVisibilityOptionHolder

class PasswordUiTextField(
    val label: LbcTextSpec?,
    override val initialValue: String,
    val placeholder: LbcTextSpec?,
    override val isFieldInError: (String) -> UiFieldError?,
    override val visibilityOptionData: PasswordVisibilityOptionData,
    override val id: String,
    override val savedStateHandle: SavedStateHandle,
    private val passwordUiFieldData: PasswordUiFieldData = PasswordUiFieldDataDefault(),
    override val onValueChange: (String) -> Unit = {},
    override val readOnly: Boolean = false,
    override val enabled: Boolean = true,
    private val maxLine: Int = 1,
    private val keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    private val onKeyboardActions: KeyboardActionHandler = KeyboardActionHandler { /* no-op */ },
) : UiField<String, String>(), PasswordVisibilityOptionHolder {
    private val mIsValueVisible: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val isValueVisible: StateFlow<Boolean> = mIsValueVisible.asStateFlow()

    override fun onVisibilityToggle() {
        mIsValueVisible.value = !isValueVisible.value
    }

    override val options: List<UiFieldOption> = listOf(
        PasswordVisibilityFieldOption(this),
    )

    @Composable
    override fun Composable(modifier: Modifier) {
        val collectedValue by mValue.collectAsState()
        val collectedIsValueVisible by isValueVisible.collectAsState()
        val collectedError by error.collectAsState()

        passwordUiFieldData.ComposeTextField(
            value = collectedValue,
            onValueChange = {
                value = it
                dismissError()
            },
            modifier = modifier.onFocusEvent {
                if (!it.hasFocus && hasBeenFocused) {
                    checkAndDisplayError()
                } else {
                    hasBeenFocused = true
                    dismissError()
                }
            },
            placeholder = placeholder,
            label = label,
            trailingIcon = if (options.isNotEmpty()) {
                { options.forEach { it.Composable(modifier = Modifier) } }
            } else {
                null
            },
            isValueVisible = collectedIsValueVisible,
            keyboardOptions = keyboardOptions,
            onKeyboardActions = onKeyboardActions,
            maxLine = maxLine,
            readOnly = false,
            error = collectedError,
            interactionSource = null,
        )
    }

    override fun formToDisplay(value: String): String = value

    override fun saveToSavedStateHandle(value: String, savedStateHandle: SavedStateHandle) {
        savedStateHandle[id] = value
    }

    override fun restoreFromSavedStateHandle(savedStateHandle: SavedStateHandle): String? = savedStateHandle[id]
}

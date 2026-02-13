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

package studio.lunabee.compose.foundation.uifield

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import studio.lunabee.compose.foundation.uifield.field.UiFieldError

/**
 * Base class for uifields
 *
 * @param Form internal form type
 * @param Display user displayed type
 */
abstract class UiField<Form, Display> {
    abstract val id: String
    abstract val savedStateHandle: SavedStateHandle
    abstract val initialValue: Form
    abstract val options: List<UiFieldOption>
    abstract val isFieldInError: (Form) -> UiFieldError?
    abstract val onValueChange: (Form) -> Unit
    abstract val readOnly: Boolean
    abstract val enabled: Boolean

    protected val mValue: MutableStateFlow<Form> by lazy {
        MutableStateFlow(
            restoreFromSavedStateHandle(savedStateHandle) ?: initialValue,
        )
    }

    val isInError: Flow<Boolean> by lazy { mValue.map { isFieldInError(it) != null } }

    protected val error: MutableStateFlow<UiFieldError?> = MutableStateFlow(null)

    /**
     * Focus has been acquired by the field at least once
     */
    protected var hasBeenFocused: Boolean = false

    var value: Form
        get() = mValue.value
        set(value) {
            saveToSavedStateHandle(value, savedStateHandle)
            mValue.value = value
            onValueChange(value)
        }

    /**
     * Run field validation and display error if any
     *
     * @return true if there is an error
     */
    fun checkAndDisplayError(): Boolean {
        val error = isFieldInError(value)
        error?.let(::displayError)
        return error != null
    }

    fun dismissError() {
        error.value = null
    }

    fun displayError(displayError: UiFieldError) {
        error.value = displayError
    }

    /**
     * Convert from form model [Form] to user visible model [Display]
     */
    abstract fun formToDisplay(value: Form): Display

    /**
     * Save form model [Form] to saved state handle
     */
    abstract fun saveToSavedStateHandle(value: Form, savedStateHandle: SavedStateHandle)

    /**
     * Restore form model [Form] from saved state handle
     */
    abstract fun restoreFromSavedStateHandle(savedStateHandle: SavedStateHandle): Form?

    @Composable
    abstract fun Composable(modifier: Modifier)
}

abstract class BooleanUiField : UiField<Boolean, Boolean>() {
    override fun formToDisplay(value: Boolean): Boolean = value

    override fun saveToSavedStateHandle(value: Boolean, savedStateHandle: SavedStateHandle) {
        savedStateHandle[id] = value
    }

    override fun restoreFromSavedStateHandle(savedStateHandle: SavedStateHandle): Boolean? = savedStateHandle[id]
}

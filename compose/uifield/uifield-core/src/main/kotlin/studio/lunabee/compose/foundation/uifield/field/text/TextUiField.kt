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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import studio.lunabee.compose.core.LbcTextSpec
import studio.lunabee.compose.foundation.uifield.UiField
import studio.lunabee.compose.foundation.uifield.field.style.DefaultUiFieldStyleData
import studio.lunabee.compose.foundation.uifield.field.style.UiFieldStyleData

/**
 * Base class for text-based uifields working with TextFieldValue
 */
abstract class TextUiField<T>(
    private val uiFieldStyleData: UiFieldStyleData = DefaultUiFieldStyleData(),
) : UiField<T, TextFieldValue>() {

    abstract val placeholder: LbcTextSpec?
    abstract val label: LbcTextSpec?

    @Composable
    final override fun Composable(modifier: Modifier) {
        Composable(modifier, uiFieldStyleData)
    }

    @Composable
    abstract fun Composable(modifier: Modifier, uiFieldStyleData: UiFieldStyleData)
}

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

package studio.lunabee.compose.foundation.uifield.form

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import studio.lunabee.compose.foundation.uifield.UiField

/**
 * Represents a form with a map of [UiField]
 */
open class Form(
    val fields: LinkedHashMap<String, UiField<*, *>>,
) {
    constructor(fields: Collection<UiField<*, *>>) : this(
        LinkedHashMap<String, UiField<*, *>>().apply {
            fields.associateByTo(this) { it.id }
        },
    )

    /**
     * Emits true when all fields are valid, false otherwise
     */
    open fun observeFieldsValidity(): Flow<Boolean> {
        return combine(fields.map { it.value.isInError }) { errors ->
            errors.all { !it }
        }.distinctUntilChanged()
    }

    /**
     * Checks all fields validity and displays errors on them if any
     *
     * @return true if any field is invalid, false otherwise
     */
    open fun checkAndDisplayErrors(): Boolean {
        return fields.map { it.value.checkAndDisplayError() }.any { it }
    }
}

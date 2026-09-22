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

package studio.lunabee.compose.presenter.ksp

/**
 * KSP option asking a DI specific processor to generate the reducer factories itself, with its DI annotations.
 */
const val AnnotateFactoryOption: String = "studio.lunabee.presenter.annotateFactory"

/**
 * KSP option asking the lbcpresenter-koin-ksp processor to generate the Koin module binding the generated factories.
 */
const val GenerateKoinModuleOption: String = "studio.lunabee.presenter.generateKoinModule"

/**
 * Value of the boolean KSP [option], null when it is unset or set to a value which is neither `true` nor `false`.
 *
 * [onMalformed] is called with the raw value in the latter case, so a single processor reports it.
 */
fun Map<String, String>.booleanKspOption(
    option: String,
    onMalformed: (rawValue: String) -> Unit = {},
): Boolean? {
    val rawValue = this[option] ?: return null
    val value = rawValue.trim().toBooleanStrictOrNull()
    if (value == null) {
        onMalformed(rawValue)
    }
    return value
}

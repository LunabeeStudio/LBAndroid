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

package studio.lunabee.parse.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Parse reference to an object of another class, serialized as `{"__type": "Pointer", "className": …, "objectId": …}`.
 *
 * @property className Parse class of the referenced object.
 * @property objectId Id of the referenced object.
 * @property type Parse type marker, always `Pointer`.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ParsePointer(
    val className: String,
    val objectId: ParseObjectId,
) {
    @EncodeDefault
    @SerialName("__type")
    val type: String = "Pointer"
}

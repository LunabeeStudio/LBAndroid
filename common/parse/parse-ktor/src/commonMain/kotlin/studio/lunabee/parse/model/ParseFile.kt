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
 * Parse file attached to an object, serialized as `{"__type": "File", "name": …, "url": …}`.
 *
 * @property name Name given by the server, unique per upload.
 * @property url Public URL of the file.
 * @property type Parse type marker, always `File`.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ParseFile(
    val name: String,
    val url: String,
) {
    @EncodeDefault
    @SerialName("__type")
    val type: String = "File"
}

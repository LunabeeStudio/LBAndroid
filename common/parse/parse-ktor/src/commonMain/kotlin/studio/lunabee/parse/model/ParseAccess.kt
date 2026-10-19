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

import kotlinx.serialization.Serializable

/**
 * Permissions an ACL grants one user or role, a null permission being left out, so denied.
 *
 * @property read Whether the user or role can read the object.
 * @property write Whether the user or role can modify the object.
 */
@Serializable
data class ParseAccess(
    val read: Boolean? = null,
    val write: Boolean? = null,
)

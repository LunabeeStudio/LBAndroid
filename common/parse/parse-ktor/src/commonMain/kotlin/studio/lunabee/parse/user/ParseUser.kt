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

package studio.lunabee.parse.user

import kotlinx.serialization.Serializable
import studio.lunabee.parse.model.ParseObjectId

/**
 * Parse user returned by the user endpoints. Fields specific to the app are not decoded.
 *
 * @property objectId Id of the user.
 * @property sessionToken Session token of the user, to give to `LBParseSessionTokenProvider`.
 * Absent when the server does not return one.
 */
@Serializable
data class ParseUser(
    val objectId: ParseObjectId,
    val sessionToken: String? = null,
)

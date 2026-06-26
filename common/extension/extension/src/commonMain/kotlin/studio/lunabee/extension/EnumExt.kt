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

package studio.lunabee.extension

import co.touchlab.kermit.Logger
import studio.lunabee.logger.LBLogger

inline fun <reified T : Enum<T>> enumValueOfOrNull(name: String?, logger: Logger? = EnumExt.enumLogger): T? = try {
    name?.let { enumValueOf<T>(it) }
} catch (e: IllegalArgumentException) {
    logger?.e("Failed to get enum ${T::class.simpleName} for string \"$name\"")
    null
}

inline fun <reified T : Enum<T>> enumValueOfOrElse(name: String?, fallback: T): T = try {
    name?.let { enumValueOf<T>(it) }
} catch (_: IllegalArgumentException) {
    null
} ?: fallback

object EnumExt {
    val enumLogger: Logger = LBLogger.get("EnumExt")
}

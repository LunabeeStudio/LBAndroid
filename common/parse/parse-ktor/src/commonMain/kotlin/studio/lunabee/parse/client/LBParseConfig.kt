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

package studio.lunabee.parse.client

import studio.lunabee.ktor.LBKtorKermit
import studio.lunabee.parse.error.LBParseException

/**
 * Configuration of an [LBParseClient].
 *
 * @property serverUrl Parse server URL, mount path included.
 * @property applicationId Parse application id, sent as `X-Parse-Application-Id`.
 * @property clientKey Parse client key, sent as `X-Parse-Client-Key`.
 * @property sessionTokenProvider Supplies the session token of every request.
 * @property installationIdProvider Supplies the installation id of every request, none by default.
 * @property maxRetries Number of retries after an `IOException`, for every HTTP method. `0` disables retry.
 * @property logLevel HTTP logging, none by default because requests carry credentials and session tokens.
 * @property mapErr Converts every [LBParseException] to the error type of the app, identity by default.
 */
class LBParseConfig(
    val serverUrl: String,
    val applicationId: String,
    val clientKey: String,
    val sessionTokenProvider: LBParseSessionTokenProvider,
    val installationIdProvider: LBParseInstallationIdProvider? = null,
    val maxRetries: Int = 1,
    val logLevel: LBKtorKermit.LogLevel = LBKtorKermit.LogLevel.None,
    val mapErr: suspend (LBParseException) -> Throwable = { it },
)

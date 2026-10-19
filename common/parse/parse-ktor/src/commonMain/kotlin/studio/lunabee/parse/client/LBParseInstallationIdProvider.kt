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

/**
 * Supplies the Parse installation id of the app. The app owns the id.
 */
fun interface LBParseInstallationIdProvider {
    /**
     * @return the installation id sent as `X-Parse-Installation-Id` on every request, or `null` to send no header.
     */
    suspend fun installationId(): String?
}

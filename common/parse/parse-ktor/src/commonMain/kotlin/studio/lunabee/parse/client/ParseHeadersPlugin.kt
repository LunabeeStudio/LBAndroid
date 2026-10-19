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

import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.util.AttributeKey

private const val ApplicationIdHeader: String = "X-Parse-Application-Id"
private const val ClientKeyHeader: String = "X-Parse-Client-Key"
private const val SessionTokenHeader: String = "X-Parse-Session-Token"
private const val InstallationIdHeader: String = "X-Parse-Installation-Id"

private val WithoutSessionTokenKey: AttributeKey<Unit> = AttributeKey("ParseWithoutSessionToken")

internal fun parseHeadersPlugin(config: LBParseConfig): ClientPlugin<Unit> = createClientPlugin("ParseHeaders") {
    onRequest { request, _ ->
        request.headers.append(ApplicationIdHeader, config.applicationId)
        request.headers.append(ClientKeyHeader, config.clientKey)
        config.installationIdProvider?.installationId()?.let { request.headers.append(InstallationIdHeader, it) }
        if (WithoutSessionTokenKey !in request.attributes) {
            config.sessionTokenProvider.sessionToken()?.let { request.headers.append(SessionTokenHeader, it) }
        }
    }
}

/**
 * Sends this request without the session token, so a stale session cannot fail it.
 */
fun HttpRequestBuilder.withoutSessionToken() {
    attributes.put(WithoutSessionTokenKey, Unit)
}

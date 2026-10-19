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

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import kotlinx.io.IOException
import studio.lunabee.ktor.LBKtorExceptionHandler
import studio.lunabee.ktor.LBKtorJson
import studio.lunabee.ktor.LBKtorKermit
import studio.lunabee.parse.error.LBParseException
import studio.lunabee.parse.error.toParseException

private const val RetryDelayMillis: Long = 1_000

/**
 * Client of the Parse REST API. Failures surface as [studio.lunabee.parse.error.LBParseException], or as the type
 * returned by [LBParseConfig.mapErr].
 *
 * Closing it closes [engine] too.
 *
 * @param engine HTTP engine owned by this client, chosen by the app.
 * @param config Server, credentials, providers, retry and logging.
 * @param configure Installs extra plugins, such as monitoring, on the underlying [httpClient].
 */
class LBParseClient(
    private val engine: HttpClientEngine,
    config: LBParseConfig,
    configure: HttpClientConfig<*>.() -> Unit = {},
) : AutoCloseable {
    internal val mapErr: suspend (LBParseException) -> Throwable = config.mapErr

    /**
     * Client resolving relative paths against [LBParseConfig.serverUrl], for app-specific Parse calls.
     */
    val httpClient: HttpClient = HttpClient(engine) {
        install(parseErrorMappingPlugin(config.mapErr))
        install(LBKtorExceptionHandler) {
            mapErr = { exception -> exception.toParseException() }
        }
        install(LBKtorJson) {
            url = config.serverUrl.trimEnd('/') + '/'
        }
        install(LBKtorKermit) {
            logLevel = config.logLevel
        }
        install(parseHeadersPlugin(config))
        if (config.maxRetries > 0) {
            install(HttpRequestRetry) {
                retryIf(maxRetries = config.maxRetries) { _, _ -> false }
                retryOnExceptionIf(maxRetries = config.maxRetries) { _, cause -> cause is IOException }
                constantDelay(millis = RetryDelayMillis, randomizationMs = 0, respectRetryAfterHeader = false)
            }
        }
        configure()
    }

    override fun close() {
        httpClient.close()
        engine.close()
    }
}

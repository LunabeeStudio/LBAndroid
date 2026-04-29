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

package com.lunabee.demo

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import studio.lunabee.ktor.LBKtorExceptionHandler
import studio.lunabee.ktor.LBKtorJson
import studio.lunabee.ktor.LBKtorKermit
import studio.lunabee.monitoring.core.LBMonitoring
import studio.lunabee.monitoring.ktor.LBKtorMonitoring

internal class DemoRemoteDatasourceImpl(
    monitoring: LBMonitoring,
) {
    private val httpClient: HttpClient = HttpClient {
        install(LBKtorKermit)
        install(LBKtorExceptionHandler) {
            mapErr = { AppException() }
        }
        install(LBKtorMonitoring) {
            this.monitoring = monitoring
        }
        install(LBKtorJson) { url = "https://dogapi.dog" }
    }

    suspend fun getDogFact(): String {
        return try {
            httpClient.get(urlString = "api/v2/facts") {
                parameter("limit", 1)
            }.body<ApiDogFact>().data.first().attributes.body
        } catch (e: AppException) {
            "An expected error occurred"
        }
    }

    suspend fun getDogFact404(): String {
        return try {
            httpClient.get(urlString = "api/fa") {
                parameter("limit", 1)
            }.body<ApiDogFact>().data.first().attributes.body
        } catch (e: AppException) {
            "An expected error occurred"
        }
    }
}

@Serializable
internal data class ApiDogFact(
    val data: List<ApiDogFactData>,
)

@Serializable
internal data class ApiDogFactData(
    val attributes: ApiDogFactAttributes,
)

@Serializable
internal data class ApiDogFactAttributes(
    val body: String,
)

internal class AppException : Throwable()

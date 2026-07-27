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

plugins {
    id("lunabee.android-library-conventions")
    id("lunabee.library-publish-conventions")
}

description = "OkHttp integration to monitoring library"
version = AndroidConfig.MONITORING_OKHTTP_VERSION

android {
    namespace = "studio.lunabee.onitoring.okhttp"
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation(libs.kotlinxDatetime)
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.okhttp)

    implementation(projects.loggerKermit)
    implementation(projects.monitoringCore)
}

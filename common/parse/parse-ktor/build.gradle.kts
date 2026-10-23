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
    id("lunabee.kmp-library-conventions")
    id("lunabee.library-publish-conventions")
    alias(libs.plugins.kotlinxSerialization)
}

description = "Parse Server REST client for Kotlin Multiplatform built on Ktor"
version = AndroidConfig.PARSE_KTOR_VERSION

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinxSerializationJson)
            api(libs.ktorClientCore)

            implementation(projects.ktorCore)
            implementation(projects.ktorJson)
            api(projects.ktorKermit)
        }
        commonTest.dependencies {
            implementation(project.dependencies.platform(libs.kotlinxCoroutinesBom))

            implementation(libs.kotlinTest)
            implementation(libs.kotlinxCoroutinesTest)

            implementation(projects.parseKtorTest)
        }
    }
}

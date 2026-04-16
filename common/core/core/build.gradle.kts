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
}

description = "Lunabee Studio Kotlin core library"
version = AndroidConfig.CORE_VERSION

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project.dependencies.platform(libs.kotlinxCoroutinesBom))

            implementation(libs.kotlinxCoroutinesCore)

            api(projects.loggerKermit)
        }
        commonTest.dependencies {
            implementation(libs.kotlinTest)
            implementation(libs.kotlinxCoroutinesTest)
        }
        jvmTest.dependencies {
            implementation(project.dependencies.platform(libs.junitJupiterBom))

            implementation(libs.junitJupiter)
            implementation(libs.kotlinTest)
            implementation(libs.kotlinxCoroutinesTest)
        }
        nativeMain {
        }
    }
}

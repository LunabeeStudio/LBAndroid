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
    id("lunabee.kmp-android-library-conventions")
    id("lunabee.library-publish-conventions")
}

description = "Lunabee Studio synchronization engine (storage-agnostic core)"
version = AndroidConfig.SYNCHRONIZATION_CORE_VERSION

kotlin {
    android {
        namespace = "studio.lunabee.synchronization"

        withHostTest {
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }

        commonMain.dependencies {
            implementation(project.dependencies.platform(libs.kotlinxCoroutinesBom))

            implementation(libs.kotlinxCoroutinesCore)

            api(projects.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinTest)
            implementation(libs.kotlinxCoroutinesTest)
        }
        androidMain.dependencies {
            implementation(libs.androidxAppcompat)
            implementation(libs.androidxCore)
            implementation(libs.androidxLifecycleProcess)
            implementation(libs.kotlinxCoroutinesAndroid)

            implementation(projects.coreAndroid)
            implementation(projects.loggerKermit)
        }
    }
}

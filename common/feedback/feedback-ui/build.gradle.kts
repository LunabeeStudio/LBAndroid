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
    id(libs.plugins.composePlugin.get().pluginId)
    alias(libs.plugins.composeMultiplatform)
}

description = "Compose Multiplatform screens for in-app feedback"
version = AndroidConfig.FEEDBACK_UI_VERSION

kotlin {
    android {
        namespace = "studio.lunabee.feedback.ui"
        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true

        withHostTest {
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("androidx.compose.material3.ExperimentalMaterial3Api")
        }
        commonMain.dependencies {
            implementation(project.dependencies.platform(libs.kotlinxCoroutinesBom))

            implementation(libs.jetbrainsComponentsResources)
            implementation(libs.jetbrainsFoundation)
            implementation(libs.jetbrainsLifecycleRuntimeCompose)
            implementation(libs.jetbrainsMaterial3)
            implementation(libs.jetbrainsRuntime)
            implementation(libs.jetbrainsUi)
            implementation(libs.kotlinxDatetime)
            implementation(libs.kotlinxViewModel)

            api(projects.feedbackCore)
        }
        androidMain.dependencies {
            implementation(libs.androidxActivityCompose)
            implementation(libs.androidxCore)
            implementation(libs.androidxLifecycleProcess)
        }
        commonTest.dependencies {
            implementation(libs.kotlinTest)
            implementation(libs.kotlinxCoroutinesTest)

            implementation(projects.feedbackTest)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "studio.lunabee.feedback.ui.res"
    generateResClass = always
}

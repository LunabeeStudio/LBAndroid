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
    alias(libs.plugins.kotlinxSerialization)
}

description = "Compose UI for monitoring"
version = AndroidConfig.MONITORING_UI_VERSION

kotlin {

    android {
        namespace = "studio.lunabee.monitoring.ui"
        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
    }

    sourceSets {
        all {
            languageSettings.optIn("androidx.compose.foundation.ExperimentalFoundationApi")
            languageSettings.optIn("androidx.compose.material3.ExperimentalMaterial3Api")
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
        }
        commonMain.dependencies {
            implementation(project.dependencies.platform(libs.koinBom))

            implementation(libs.androidxPagingCommon)
            implementation(libs.composeNavigation)
            implementation(libs.jetbrainsComponentsResources)
            implementation(libs.jetbrainsFoundation)
            implementation(libs.jetbrainsMaterial3)
            implementation(libs.jetbrainsRuntime)
            implementation(libs.jetbrainsUi)
            implementation(libs.koinCompose)
            implementation(libs.koinCore)
            implementation(libs.koinViewmodel)
            implementation(libs.kotlinxDatetime)
            implementation(libs.kotlinxSerializationJson)
            implementation(libs.kotlinxViewModel)

            implementation(projects.monitoringCore)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "studio.lunabee.monitoring.ui.res"
    generateResClass = always

    customDirectory(
        sourceSetName = "iosMain",
        directoryProvider = provider { layout.projectDirectory.dir("src/iosMain/iosResources") },
    )

    customDirectory(
        sourceSetName = "androidMain",
        directoryProvider = provider { layout.projectDirectory.dir("src/androidMain/androidResources") },
    )
}

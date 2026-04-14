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
    id("lunabee.android-compose-library-conventions")
    id("lunabee.library-publish-conventions")
    alias(libs.plugins.kotlinxSerialization)
}

android {
    resourcePrefix = "lbc_uifield_"
    namespace = "studio.lunabee.compose.uifield.countrypicker"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

description = "country picker ui field"
version = AndroidConfig.LBCUIFIELD_COUNTRYPICKER_VERSION

dependencies {
    coreLibraryDesugaring(libs.desugarJdk)

    implementation(platform(libs.composeBom))

    implementation(libs.androidxAppcompat)
    implementation(libs.composeMaterial3)
    implementation(libs.composeUi)
    implementation(libs.hbb20Countrycodepicker)
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.normalize)

    implementation(projects.compose.core)
    implementation(projects.compose.image)
    implementation(projects.compose.uifieldCore)
}

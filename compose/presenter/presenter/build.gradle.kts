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
}

android {
    // resourcePrefix("lbc_presenter_")
    namespace = "studio.lunabee.compose.presenter"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

description = "Compose implementation of MVI pattern"
version = AndroidConfig.LBCPRESENTER_VERSION

dependencies {
    implementation(platform(libs.composeBom))

    implementation(libs.androidxActivityCompose)
    implementation(libs.androidxLifecycleRuntimeCompose)
    implementation(libs.androidxLifecycleViewmodelAndroid)
    implementation(libs.composeMaterial3)
    implementation(libs.composeUi)
    implementation(libs.touchlabKermit)

    implementation(projects.compose.core)
    implementation(projects.compose.image)
    api(projects.compose.presenterAnnotations)

    androidTestImplementation(libs.kotlinTestJunit)
    androidTestImplementation(libs.kotlinxCoroutinesTest)

    testImplementation(libs.androidxComposeUiTestJunit)
    testImplementation(libs.kotlinTestJunit)
    testImplementation(libs.kotlinxCoroutinesTest)
    testImplementation(projects.compose.robolectrictest)
    testImplementation(libs.robolectric)
}

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

android {
    namespace = "studio.lunabee.extension"
    resourcePrefix = "lb_e_"
}

description = "Lunabee Studio extensions lib"
version = AndroidConfig.EXTENSIONS_ANDROID_VERSION

dependencies {
    // AndroidX
    implementation(libs.androidxAppcompat)
    implementation(libs.androidxCore)
    implementation(libs.androidxExifinterface)
    implementation(libs.androidxLifecycleRuntime)
    implementation(libs.androidxRecyclerview)
    implementation(libs.googleAndroidMaterial)

    implementation(projects.extension)
    // Lunabee
    implementation(projects.loggerKermit)

    androidTestImplementation(libs.androidxPreferenceKtx)
    androidTestImplementation(libs.androidxTestCoreKtx)
    androidTestImplementation((libs.junit4))
    androidTestImplementation(libs.kotlinTestJunit)
}

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

description = "A global blocking loading view for Compose"
version = AndroidConfig.LOADING_VERSION

android {
    resourcePrefix = "lb_loading_"
    namespace = "studio.lunabee.loading.compose"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    implementation(platform(libs.composeBom))

    implementation(libs.androidxActivityCompose)
    implementation(libs.androidxLifecycleRuntimeCompose)
    implementation(libs.composeFoundation)
    implementation(libs.composeMaterial3)

    implementation(projects.coreCompose)
    lintPublish(projects.loadingChecks)
    api(projects.loadingCore)
}

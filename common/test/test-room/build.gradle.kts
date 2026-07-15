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
    alias(libs.plugins.androidxRoom)
    alias(libs.plugins.ksp)
}

android {
    namespace = "studio.lunabee.test.room"

    // Exported Room schemas must be readable by Robolectric as assets. Attaching them to the `debug`
    // source set keeps them out of the published release artifact.
    sourceSets {
        getByName("debug").assets.directories.add("$projectDir/schemas")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

description = "Lunabee Studio Kotlin test library for Room database"
version = AndroidConfig.TEST_ROOM_VERSION

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Fixtures used to exercise GlobalRoomMigrationTestHelper against a real Room database on the JVM.
    add("kspTest", libs.androidxRoomCompiler)
    api(libs.androidxRoomTestingAndroid)
    implementation(libs.androidxTestMonitor)

    testImplementation(libs.androidxRoomRuntime)
    testImplementation(libs.androidxTestCoreKtx)
    testImplementation(libs.kotlinTestJunit)
    testImplementation(libs.robolectric)
}

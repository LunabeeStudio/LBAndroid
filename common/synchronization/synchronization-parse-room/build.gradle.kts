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
    id("lunabee.kmp-android-library-no-ios-conventions")
    id("lunabee.library-publish-conventions")
}

description = "Lunabee Studio Parse-backed Room synchronization lib"
version = AndroidConfig.SYNCHRONIZATION_PARSE_ROOM_VERSION

kotlin {
    android {
        // Distinct namespace from the :synchronization artifact so generated R/BuildConfig don't collide.
        namespace = "studio.lunabee.synchronization.parseroom"
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }

        commonMain.dependencies {
            // Room annotations (@Upsert) leak into the generic LBRoomSyncDao base; the concrete @Dao
            // subclasses live in the consumer module and are processed by its own KSP (no KSP here).
            api(libs.androidxRoomRuntime)
        }

        androidMain.dependencies {
            api(project.dependencies.platform(libs.kotlinxCoroutinesBom))

            // implementation: the Parse coroutines suspend extensions (ParseQuery.find/get,
            // ParseObject.save) are called only internally by the manager base classes and never
            // leak into a protected/public signature, so they stay off the API classpath.
            implementation(libs.parseCoroutines)
            api(libs.kotlinxCoroutinesCore)
            api(libs.parseLiveQuery)
            api(libs.parseSdk)

            implementation(projects.loggerKermit)
            // api: the manager base classes leak Parse/coroutine/synchronization types in their
            // public signatures and are subclassed by consumers, so those need them on the compile classpath.
            api(projects.synchronization)
        }
    }
}

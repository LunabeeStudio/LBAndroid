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

// KMP Android library WITHOUT the Apple/iOS targets. Same as `lunabee.kmp-android-library-conventions`
// but it does NOT apply `lunabee.kmp-library-conventions` (which adds the SKIE plugin and the
// iosArm64/iosSimulatorArm64 frameworks). Used by modules whose published transitive dependencies are
// not available on Maven Central for iOS (e.g. androidx.datastore is Google-Maven-only), so an iOS
// publication could not be consumed/validated. They stay commonMain + androidMain, JVM-host tested.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    android {
        compileSdk = AndroidConfig.CompileSdk
        minSdk = AndroidConfig.MinSdk
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        freeCompilerArgs.add("-opt-in=kotlin.concurrent.atomics.ExperimentalAtomicApi")
    }
}

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

import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestReport

plugins {
    id("com.android.library")
}

val libs = extensions
    .getByType<VersionCatalogsExtension>()
    .named("libs")

android {
    compileSdk = AndroidConfig.CompileSdk

    defaultConfig {
        minSdk = AndroidConfig.MinSdk
        testOptions.targetSdk = AndroidConfig.TargetSdk
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = AndroidConfig.JDK_VERSION
        targetCompatibility = AndroidConfig.JDK_VERSION
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    androidTestImplementation(libs.findLibrary("androidxTestRunner").get())
    testImplementation(libs.findLibrary("kotlinTestJunit").get())
}

kotlin.compilerOptions.jvmTarget.set(AndroidConfig.JVM_TARGET)

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(AndroidConfig.JDK_VERSION.toString()))
    }
}

val hostTestTasks = tasks.withType<Test>()

tasks.register<TestReport>("allTests") {
    description = "Runs the tests for all targets and create aggregated report"
    group = "verification"
    destinationDirectory.set(layout.buildDirectory.dir("reports/tests/allTests"))
    testResults.from(hostTestTasks.map { it.binaryResultsDirectory })
    dependsOn(hostTestTasks)
}

tasks.register<Delete>("cleanAllTests") {
    description = "Deletes all the test results."
    delete(
        layout.buildDirectory.dir("reports/tests/allTests"),
        layout.buildDirectory.dir("test-results"),
    )
}

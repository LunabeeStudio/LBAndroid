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
    id("org.jetbrains.kotlin.multiplatform")
}

description = "Lunabee Studio Kotlin/Native CLI chronometer demo"

// Forward `-Pargs="5 --verbose"` to the runDebug/Release executable tasks
// (Kotlin/Native run tasks do not accept Gradle's `--args` flag).
val demoArgs: Provider<String> = providers.gradleProperty("args")

kotlin {
    listOf(
        macosArm64(),
        linuxX64(),
        mingwX64(),
    ).forEach { target ->
        target.binaries.executable {
            entryPoint = "studio.lunabee.democli.main"
            runTaskProvider?.configure {
                args(
                    demoArgs.orNull
                        ?.split(' ')
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                )
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project.dependencies.platform(libs.kotlinxCoroutinesBom))

            implementation(libs.kotlinxCoroutinesCore)

            implementation(projects.core)
        }
    }
}

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

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        }
        mavenLocal()
    }
}

rootProject.name = "LBAndroid"

includeBuild("build-logic")

include(
    "demo-compose",
    "ios-demo",
)

/**
 * Add module locate in ./compose folder. Subproject :compose will be used.
 */
fun addComposeModule(vararg projectRelPath: String) {
    projectRelPath.forEach { path ->
        val projectName = ":compose:${path.split(File.separator).last()}"
        val projectDir = File("compose/$path")
        addModule(projectName, projectDir)
    }
}

/**
 * Add module locate in ./common folder
 */
fun addCommonModule(vararg projectRelPath: String) {
    projectRelPath.forEach { path ->
        val projectName = ":${path.split(File.separator).last()}"
        val projectDir = File("common/$path")
        addModule(projectName, projectDir)
    }
}

fun addModule(path: String, projectDir: File, name: String = projectDir.name) {
    include(path)
    project(path).apply {
        this.projectDir = projectDir
        this.name = name
    }
}

addComposeModule(
    "material-color-utilities",
    "accessibility",
    "test/androidtest",
    "core",
    "crop",
    "foundation",
    "glance",
    "haptic",
    "image",
    "theme",
    "test/robolectrictest",
    "presenter/presenter",
    "presenter/presenter-koin",
    "uifield/uifield-core",
    "uifield/uifield-countrypicker",
    "uifield/uifield-phonepicker",
    "uifield/uifield-form",
)

addCommonModule(
    "platform",
    "logger/logger-kermit",
    "logger/logger-kermit-crashlytics",
    "core/core",
    "core/core-compose",
    "core/core-android",
    "extension/extension",
    "extension/extension-android",
    "test",
    "ktor/ktor-core",
    "ktor/ktor-kermit",
    "ktor/ktor-json",
    "loading/loading-core",
    "loading/loading-compose",
    "loading/loading-hilt",
    "loading/loading-koin",
    "monitoring/monitoring-core",
    "monitoring/monitoring-ktor",
    "monitoring/monitoring-okhttp",
    "monitoring/monitoring-room",
    "monitoring/monitoring-ui",
)
addModule(":loading-checks", File("common/loading/loading-compose/checks"), "loading-checks")

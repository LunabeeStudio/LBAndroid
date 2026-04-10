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
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.composeMultiplatform)
    id(libs.plugins.composePlugin.get().pluginId)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach {
        it.binaries.framework {
            baseName = "iosDemo"
            isStatic = true
            export(projects.monitoringCore)
            export(projects.monitoringUi)
            export(projects.monitoringRoom)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktorClientIos)

            implementation(projects.ktorCore)
            implementation(projects.ktorJson)
            implementation(projects.ktorKermit)
            api(projects.monitoringCore)
            implementation(projects.monitoringKtor)
            api(projects.monitoringRoom)
            api(projects.monitoringUi)
        }
    }
}

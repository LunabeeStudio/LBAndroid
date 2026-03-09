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
    id("lunabee.kmp-android-library-conventions")
    id("lunabee.library-publish-conventions")
    alias(libs.plugins.androidxRoom)
    alias(libs.plugins.ksp)
}

description = "Room persistence layer implementation for monitoring"
version = AndroidConfig.MONITORING_ROOM_VERSION

kotlin {
    android {
        namespace = "studio.lunabee.monitoring.room"
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
        }

        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.koinBom))

            implementation(libs.koinAndroid)
        }

        commonMain.dependencies {
            implementation(project.dependencies.platform(libs.koinBom))

            implementation(libs.androidxRoomPaging)
            implementation(libs.androidxRoomRuntime)
            implementation(libs.androidxSqliteBundled)
            implementation(libs.koinCore)
            implementation(libs.kotlinxDatetime)

            implementation(projects.monitoringCore)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidxRoomCompiler)
    add("kspIosArm64", libs.androidxRoomCompiler)
    add("kspIosSimulatorArm64", libs.androidxRoomCompiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}

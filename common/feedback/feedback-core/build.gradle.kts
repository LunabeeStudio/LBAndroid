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
    alias(libs.plugins.kotlinxSerialization)
}

description = "Core models, store and transport contract for in-app feedback"
version = AndroidConfig.FEEDBACK_CORE_VERSION

val generateFeedbackVersion = tasks.register("generateFeedbackVersion") {
    val outputDir = layout.buildDirectory.dir("generated/feedbackVersion")
    val coreVersion = AndroidConfig.FEEDBACK_CORE_VERSION
    inputs.property("version", coreVersion)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("studio/lunabee/feedback/core/metadata/FeedbackCoreVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package studio.lunabee.feedback.core.metadata\n\ninternal const val FeedbackCoreVersion: String = \"$coreVersion\"\n",
        )
    }
}

kotlin {
    android {
        namespace = "studio.lunabee.feedback.core"

        withHostTest {
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateFeedbackVersion)
        }
        commonMain.dependencies {
            implementation(project.dependencies.platform(libs.kotlinxCoroutinesBom))

            api(libs.androidxDatastorePreferencesCore)
            api(libs.kotlinxCoroutinesCore)
            implementation(libs.kotlinxDatetime)
            implementation(libs.kotlinxSerializationJson)
        }
        commonTest.dependencies {
            implementation(libs.kotlinTest)
            implementation(libs.kotlinxCoroutinesTest)

            implementation(projects.feedbackTest)
        }
        androidMain.dependencies {
            implementation(libs.androidxCore)
            implementation(libs.androidxDatastorePreferences)
        }
    }
}

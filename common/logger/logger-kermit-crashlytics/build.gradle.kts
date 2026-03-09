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
}

description = "Lunabee Studio Kotlin crashlytics for Kermit logger"
version = AndroidConfig.LOGGER_KERMIT_VERSION

kotlin {
    android {
        namespace = "studio.lunabee.logger.kermit"

        androidResources {
            this.additionalParameters += "resourcePrefix"
        }
        // FIXME http://issuetracker.google.com/issues/470478219
        //  resourcePrefix("lb_log")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.touchlabKermit)
            implementation(libs.touchlabKermitCrashlytics)
        }
    }
}

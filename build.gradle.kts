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

import studio.lunabee.library.SetAllSnapshotVersionTask

buildscript {
    dependencies {
        classpath(libs.jreleaserGradlePlugin)
    }
}

plugins {
    alias(libs.plugins.lbDetekt)
}

lbDetekt {
    val customConfig = File(project.rootProject.layout.projectDirectory.asFile, "/lunabee-detekt-config.yml")
    config.setFrom(files(lunabeeConfig, customConfig))
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    exclude("compose/presenter/template/*")
}

/*
 * Build group id based on real project path
 */
project.allprojects {
    val relativePath = projectDir.relativeTo(rootDir).path
    val groupSegments =
        relativePath
            .split("/")
            .dropWhile { it == "common" } // use root for common
            .dropLast(1) // drop project name
            .let { listOf("studio", "lunabee") + it } // add base group id

    group = groupSegments.joinToString(".")
}

tasks.register("publishList") {
    doLast {
        val publishProjects =
            project.allprojects
                .filter {
                    it.tasks.findByName("publish") != null
                }.joinToString(";") { it.name }
        println(publishProjects)
    }
}

tasks.register("setAllSnapshotVersion", SetAllSnapshotVersionTask::class.java) {
    description = "Set the snapshot version to all published projects"
    group = "publishing"
}

// Update gradle-wrapper by running `./gradlew wrapper --gradle-version latest`
tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL
}

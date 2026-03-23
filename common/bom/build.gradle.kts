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

import org.gradle.api.internal.catalog.DelegatingProjectDependency

plugins {
    `java-platform`
    id("lunabee.library-publish-conventions")
}

val rootProjectGroup: String = "studio.lunabee"
description = "Lunabee libraries bill of materials"
version = AndroidConfig.bomVersion()

dependencies {
    constraints {
        addJvmModule(projects.core)
        addModule(projects.coreAndroid)
        addModule(projects.coreCompose)
        addModule(projects.extension)
        addModule(projects.extensionAndroid)
        addJvmModule(projects.ktorCore)
        addJvmModule(projects.ktorJson)
        addJvmModule(projects.ktorKermit)
        addModule(projects.loadingCompose)
        addModule(projects.loadingChecks)
        addJvmModule(projects.loadingCore)
        addModule(projects.loadingHilt)
        addModule(projects.loadingKoin)
        addJvmModule(projects.loggerKermit)
        addModule(projects.loggerKermitCrashlytics)
        addModule(projects.test)
        addModule(projects.monitoringCore)
        addModule(projects.monitoringKtor)
        addModule(projects.monitoringOkhttp)
        addModule(projects.monitoringRoom)
        addModule(projects.monitoringUi)
    }
}

private fun DependencyConstraintHandlerScope.addModule(module: DelegatingProjectDependency) {
    this@addModule.api(module)
}

private fun DependencyConstraintHandlerScope.addJvmModule(module: DelegatingProjectDependency) {
    addModule(module)
    this@addJvmModule.api(mapOf("group" to module.group, "name" to module.name + "-jvm", "version" to module.version))
}

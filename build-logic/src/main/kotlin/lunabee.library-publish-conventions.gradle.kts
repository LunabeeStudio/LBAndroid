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

import java.util.Locale

enum class PublishType {
    Android,
    Java,
    Kmp,
    Bom,
}

plugins {
    id("org.jreleaser")
    `maven-publish`
    signing
    id("org.jetbrains.dokka")
    id("org.jetbrains.dokka-javadoc")
}

private val publishType: PublishType = when {
    project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform") -> PublishType.Kmp
    project.plugins.hasPlugin("android-library") -> PublishType.Android
    project.plugins.hasPlugin("java-library") -> PublishType.Java
    project.plugins.hasPlugin("java-platform") -> PublishType.Bom
    else -> error("Cannot determine the type of publication")
}

private val mavenCentralUsername = project.properties["mavenCentralUsername"]?.toString()
private val mavenCentralPassword = project.properties["mavenCentralPassword"]?.toString()

private val stagingDir = layout.buildDirectory
    .dir("staging-deploy")
    .get()
    .asFile

jreleaser {
    gitRootSearch.set(true)
    deploy {
        maven {
            mavenCentral {
                create("release-deploy") {
                    active.set(org.jreleaser.model.Active.RELEASE)
                    url.set("https://central.sonatype.com/api/v1/publisher")
                    stagingRepository(stagingDir.path)
                    username.set(mavenCentralUsername)
                    password.set(mavenCentralPassword)
                    verifyPom.set(false) // FIXME https://github.com/jreleaser/jreleaser.github.io/issues/85

                    // FIXME
                    //  https://github.com/jreleaser/jreleaser/issues/1746
                    applyMavenCentralRules = false
                    artifactOverride {
                        artifactId.set("${project.name.get()}-iosarm64")
                        this.jar = false
                    }
                    artifactOverride {
                        artifactId.set("${project.name.get()}-iossimulatorarm64")
                        this.jar = false
                    }
                }
            }
        }
    }

    release {
        // Disable release feature
        github {
            token.set("fake")
            skipRelease.set(true)
            skipTag.set(true)
        }
    }
}

publishing {
    repositories {
        maven {
            setUrl(stagingDir)
        }
    }

    setupPublication()
}

/**
 * Entry point for setting publication detail.
 */
fun PublishingExtension.setupPublication() {
    publications {
        when (publishType) {
            PublishType.Android -> create<MavenPublication>(project.name) {
                // version is set in project, so use after evaluate
                afterEvaluate {
                    setProjectDetails()
                    from(components["release"])
                    setPom()
                }
                val (dokkaJavadocJar, dokkaHtmlJar) = setupDokkaTasks()
                artifact(dokkaJavadocJar)
                artifact(dokkaHtmlJar)
                setupSigning(this)
            }

            PublishType.Java -> create<MavenPublication>(project.name) {
                // version is set in project, so use after evaluate
                afterEvaluate {
                    setProjectDetails()
                    setPom()
                }
                val (dokkaJavadocJar, dokkaHtmlJar) = setupDokkaTasks()
                from(components["java"])
                artifact(dokkaJavadocJar)
                artifact(dokkaHtmlJar)
                setupSigning(this)
            }

            PublishType.Kmp -> publications.withType<MavenPublication> {
                //  https://github.com/Kotlin/dokka/issues/1753
                val emptyDocsTask = tasks.register("${this.name}EmptyDocs", Jar::class.java) {
                    archiveClassifier = "javadoc"
                    archiveBaseName = "${project.name}-${this.name}"
                }
                artifact(emptyDocsTask)
                afterEvaluate {
                    setPom()
                }
                setupSigning(this)
            }

            PublishType.Bom -> create<MavenPublication>(project.name) {
                afterEvaluate {
                    setProjectDetails()
                    from(components["javaPlatform"])
                    setPom()
                }
                setupSigning(this)
            }
        }
    }
}

/**
 * Setup Maven publication from project details
 */
private fun MavenPublication.setProjectDetails() {
    this.groupId = project.group.toString()
    this.artifactId = project.name
    this.version = project.version.toString()
    logger.log(LogLevel.INFO, "Set publication details: groupId=$groupId, artifactId=$artifactId, version=$version")
}

/**
 * Setup Dokka manually to workaround https://github.com/Kotlin/dokka/issues/2956
 */
private fun setupDokkaTasks(): Pair<TaskProvider<Jar>, TaskProvider<Jar>> {
    val dokkaJavadocJar by tasks.registering(Jar::class) {
        description = "A Javadoc JAR containing Dokka Javadoc"
        from(tasks.dokkaGeneratePublicationJavadoc.flatMap { it.outputDirectory })
        archiveClassifier.set("javadoc")
    }

    val dokkaHtmlJar by tasks.registering(Jar::class) {
        description = "A HTML Documentation JAR containing Dokka HTML"
        from(tasks.dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
        archiveClassifier.set("html-doc")
    }

    return dokkaJavadocJar to dokkaHtmlJar
}

/**
 * Set POM file details.
 */
private fun MavenPublication.setPom() {
    val repoUrl = "https://github.com/LunabeeStudio/LBAndroid"
    pom {
        name.set(project.name.capitalized())
        description.set(project.description)
        url.set(repoUrl)

        organization {
            name.set("Lunabee Studio")
            url.set("https://www.lunabee.studio")
        }

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        scm {
            connection.set("git@github.com:LunabeeStudio/LBAndroid.git")
            developerConnection.set("git@github.com:LunabeeStudio/LBAndroid.git")
            url.set(repoUrl)
        }

        developers {
            developer {
                id.set("Publisher")
                name.set("Publisher Lunabee")
                email.set("publisher@lunabee.com")
            }
        }
    }
}

private fun setupSigning(publication: MavenPublication) {
    val signingKey: String? by project
    val signingPassword: String? by project
    if (signingKey != null) {
        signing {
            isRequired = true
            useInMemoryPgpKeys(signingKey, signingPassword)
            sign(publication)
        }
    }
}

afterEvaluate {
    if (publishType == PublishType.Android) {
        tasks.withType<PublishToMavenRepository>().configureEach {
            dependsOn(tasks.named("bundleReleaseAar"))
        }
    }
}

tasks.register("PrintCoordinates") {
    val group = project.group.toString()
    val name = project.name
    val version = project.version.toString()

    doLast {
        println("$group:$name:$version")
    }
}

private fun String.capitalized(): String = if (this.isEmpty()) this else this[0].titlecase(Locale.getDefault()) + this.substring(1)

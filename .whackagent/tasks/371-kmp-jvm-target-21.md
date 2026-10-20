---
issue: 371
phase:
type: bug
wiki: [[build-and-publishing]]
note:
created: 2026-10-20
---

## Context / Decisions

spec: issue #371 body (found while validating #369).

`lunabee.kmp-library-conventions` sets no JVM target and no toolchain. The `jvm()` bytecode of every KMP module therefore follows the JDK that runs Gradle: a `publishToMavenLocal` from a shell on temurin 27 produced class file 70 (Java 26) for `migration-core-jvm`, `migration-datastore-jvm`, `core-jvm` and `logger-kermit-jvm`, which Kokoriko (JDK 21) cannot load. `lunabee.android-library-conventions` and `lunabee.java-library-conventions` already pin JDK 21 (`AndroidConfig.JDK_VERSION`, `AndroidConfig.JVM_TARGET`).

Decisions:

- The fix lives in `lunabee.kmp-library-conventions` only, so `lunabee.kmp-android-library-conventions` and `lunabee.kmp-jvm-library-conventions`, which apply it, inherit it. No module `build.gradle.kts` changes.
- (autopilot assumption) Pin the Kotlin JVM toolchain to `AndroidConfig.JDK_VERSION` (21), not only `compilerOptions.jvmTarget`. The toolchain also pins the JDK API the code compiles against, so a Java 22+ API cannot slip in. It needs no new CI setup: the Android and Java conventions already require a JDK 21 toolchain on every machine that builds this repository. Rejected: `jvmTarget` only, which keeps compiling against the API of whatever JDK runs Gradle.
- (autopilot assumption) No hand-written `org.gradle.jvm.version` attribute in the published Gradle module metadata. If Gradle or the Kotlin plugin adds it on its own once the toolchain is set, that is accepted, but it is not required (YAGNI: the bytecode fix removes the `UnsupportedClassVersionError`).
- The Android target of KMP modules (`com.android.kotlin.multiplatform.library`) must not change on a JDK 21 build. If it also follows the Gradle JDK today, pinning it to 21 is in scope (same bug).
- (autopilot assumption) No module version bump and no `CHANGELOG.MD` entry. CI releases already build on JDK 21, so no published artifact changes. `CHANGELOG.MD` entries are per release, with a module list, and there is no unreleased section to hold a line without a bump.

## Acceptance criteria

- With Gradle run on JDK 27 (`JAVA_HOME` = temurin 27), `publishToMavenLocal` of `common/core/core`, `common/logger/logger-kermit` and `common/migration/migration-core` produces `-jvm` jars whose classes all have class file major version 65 (Java 21).
- With Gradle run on JDK 21, the same jars still have class file major version 65.
- The Android target classes of a KMP Android module (for example `common/logger/logger-kermit-crashlytics`) have, on a JDK 27 build, the same class file version that `master` produces on a JDK 21 build.
- `./gradlew assemble`, `./gradlew test` and `./gradlew detekt` pass.
- No `*_VERSION` constant in `AndroidConfig.kt` changes, and `CHANGELOG.MD` is untouched.

## Implementation

## Review

## Verification

## Feedback

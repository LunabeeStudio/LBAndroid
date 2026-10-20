# Build and Publishing

How modules built (convention plugins, per-module versions, KMP/SKIE), checked (detekt), shipped to Maven Central via JReleaser.

Related: [[architecture]] · [[common-libraries]] · [[presenter]] · [[synchronization]] · [[compose-ui]]

## Toolchain

| Item | Value | Where |
|---|---|---|
| JDK / JVM target | 21 | `AndroidConfig.JDK_VERSION`, `JVM_TARGET` |
| compileSdk / targetSdk / minSdk | 37 / 37 / 23 (`SynchronizationMinSdk = 24`) | `buildSrc/src/main/kotlin/BuildConfigs.kt`, `AndroidConfig.kt` |
| Kotlin, AGP, KSP, SKIE, JReleaser | catalog-managed | `gradle/libs.versions.toml` (Renovate updates, `renovate.json5`) |

## Two convention-plugin containers

| Container | Plugins | Classpath |
|---|---|---|
| `buildSrc/` | `lunabee.android-library-conventions`, `lunabee.android-compose-library-conventions`, `lunabee.kmp-library-conventions`, `lunabee.kmp-android-library-conventions`, `lunabee.kmp-jvm-library-conventions`, `lunabee.java-library-conventions`; plus `AndroidConfig`, `BuildConfigs`, `EnvConfig`, `SetAllSnapshotVersionTask` | AGP, Kotlin, Compose compiler plugin, SKIE |
| `build-logic/` (`includeBuild`) | `lunabee.library-publish-conventions` | Dokka, JReleaser; reuses `../gradle/libs.versions.toml` |

- Why two: publishing moved out of `buildSrc` into `build-logic` during AGP 9 migration (#234). Build-time plugins (`buildSrc`) and publication plugins (`build-logic`) keep separate classpaths. Don't merge.
- Every library applies one platform convention **plus** `lunabee.library-publish-conventions`.
- `buildSrc` objects (`AndroidConfig`) visible in all module scripts, incl. `common/bom`.

### What each platform convention adds

- **android-library**: `compileSdk`/`minSdk`, JDK 21 toolchain, single `release` variant w/ sources jar, `kotlin-test-junit`, `allTests` (aggregated `TestReport`) / `cleanAllTests` tasks.
- **android-compose-library**: android-library + Kotlin Compose compiler plugin.
- **kmp-library**: Kotlin Multiplatform + SKIE; JDK 21 Kotlin toolchain (`jvmToolchain`) → `jvm` + KMP `android` bytecode = Java 21 whatever JDK runs Gradle (before #371 followed Gradle JDK: class file 70 on JDK 27); `iosArm64` + `iosSimulatorArm64` **static** frameworks named after project; JUnit platform; opt-ins `-Xexpect-actual-classes`, `ExperimentalTime`, `ExperimentalAtomicApi`. Extra targets (`jvm`, `macosArm64`, `linuxX64`, `mingwX64`) declared per module.
- **kmp-android-library**: kmp-library + `com.android.kotlin.multiplatform.library` (`android { }` target).
- **kmp-jvm-library**: kmp-library + `jvm()`.
- **java-library**: `java-library`, JDK 21, sources jar (KSP processors, annotations, color utils, lint-check projects). No `maven-publish` itself; lint-check projects skip publish conventions.

## Per-module versions

- Each module sets `version = AndroidConfig.<CONST>` in its `build.gradle.kts`; constants in `buildSrc/src/main/kotlin/AndroidConfig.kt`.
- Naming rule (comment in file): module name `UPPER_CASE`, `-` → `_`, suffix `_VERSION`. Compose modules get `LBC` prefix (`LBCCORE_VERSION`, `LBCPRESENTER_KSP_VERSION`).
- Families share version via aliasing or reuse: `LBCUIFIELD_{PHONE,COUNTRY}PICKER_VERSION = LBCUIFIELD_CORE_VERSION`, `MONITORING_* = MONITORING_CORE_VERSION`, `LBCPRESENTER_{KOIN,HILT}_KSP_VERSION = LBCPRESENTER_KSP_VERSION`, `SYNCHRONIZATION_{EVENTS,CORE_DATASTORE,CORE_ROOM,PARSE_ROOM,REMOTE}_VERSION = SYNCHRONIZATION_CORE_VERSION`; `CORE_VERSION`, `KTOR_VERSION`, `LOADING_VERSION`, `LOGGER_KERMIT_VERSION` used by several modules; `:synchronization-checks` uses `SYNCHRONIZATION_CORE_VERSION` directly.
- Presenter modules versioned independently: `LBCPRESENTER_VERSION`, `LBCPRESENTER_ANNOTATIONS_VERSION`, `LBCPRESENTER_HILT_VERSION`, `LBCPRESENTER_KOIN_VERSION`, `LBCPRESENTER_KSP_VERSION` (KSP processors alias it).
- Gotcha: `compose/crop` currently uses `LBCHAPTIC_VERSION`.
- Rule matters for `setAllSnapshotVersion` (below), which derives constant names from project names.

## Versioning and changelog rules (AGENTS.MD)

- Semver per module. Major reserved for wide-reaching change (rewrite, API-surface redesign, migration consumers must plan). Narrow break (one removed symbol, dropped transitive dep, renamed param) = **minor** bump, flagged `**break:**` in changelog.
- Bump module **once per branch** — fold later work into existing unreleased bump.
- Module's transitive dependency moves → bump that module's minor too (e.g. synchronization adapters bumped with `synchronization-core`).
- Every user-visible change updates `CHANGELOG.MD`: `## YYYY-MM-DD`, fenced block listing `Module version` lines, then bullets; breaking items prefixed `**break:**`.

## BOM versioning

- `common/bom` version = `AndroidConfig.bomVersion()`: env var `ANDROID_VERSION_NAME` (`EnvConfig.EnvVersionName`) if non-blank, else `PLATFORM_VERSION`.
- Constraints reference project versions → BOM always pins what's in `AndroidConfig.kt`. Contents: [[architecture]].

## Publishing (`build-logic/src/main/kotlin/lunabee.library-publish-conventions.gradle.kts`)

- Publication type inferred from applied plugins: **Kmp** (kotlin.multiplatform) > **Android** (`android-library`) > **Java** (`java-library`) > **Bom** (`java-platform`).
- Android/Java: `from(release|java)` + Dokka `javadoc` and `html-doc` jars (Dokka wired manually, Dokka#2956). KMP: one empty `javadoc` jar per publication (Dokka#1753). Bom: `javaPlatform` component.
- POM: Apache-2.0, Lunabee Studio org, GitHub `LunabeeStudio/LBAndroid` SCM.
- Signing: in-memory PGP from Gradle properties `signingKey` / `signingPassword`, only when `signingKey` set.
- Flow: `maven-publish` writes to local staging repo `build/staging-deploy`; JReleaser (`mavenCentral` deployer, `https://central.sonatype.com/api/v1/publisher`, `active = RELEASE`) uploads it w/ `mavenCentralUsername` / `mavenCentralPassword`. GitHub release/tag disabled. `verifyPom = false` (jreleaser.github.io#85).
- **jreleaser#1746 workaround**: `applyMavenCentralRules = false` plus `artifactOverride { jar = false }` for `-iosarm64`, `-iossimulatorarm64`, `-macosarm64`, `-linuxx64`, `-mingwx64`. Native targets produce klibs only; adding new native target to any module → add its override here (#299 fixed exactly this for desktop targets).
- Android publications depend on `bundleReleaseAar`.
- Root `build.gradle.kts` puts JReleaser plugin on buildscript classpath.
- Consumers can resolve snapshots from `s01.oss.sonatype.org` (declared in `settings.gradle.kts`, documented in `README.md`).
- Validate in another local project with `./gradlew publishToMavenLocal`.

## Utility tasks

| Task | Defined in | Does |
|---|---|---|
| `publishList` | root `build.gradle.kts` | Prints `;`-joined names of projects with `publish` task (CI uses). |
| `:<path>:PrintCoordinates` | publish conventions | Prints `group:name:version`. |
| `setAllSnapshotVersion --buildNumber=N` | `buildSrc/.../SetAllSnapshotVersionTask.kt` | Rewrites `AndroidConfig.kt` **in place**: for every `maven-publish` project, appends `-alpha-<N>-<branch last segment>-SNAPSHOT` to constants matching `<NAME>_VERSION`. Regex unanchored → `LBC`-prefixed constants match; only `= "…"` literals rewritten, so aliases (`= SYNCHRONIZATION_CORE_VERSION`) follow their source; shared constants not named after project (e.g. `KTOR_VERSION`, `LOADING_VERSION`, `PLATFORM_VERSION`) untouched. |

## Detekt

- Root applies `studio.lunabee.plugin.detekt` (catalog alias `lbDetekt`) w/ Lunabee base config + `lunabee-detekt-config.yml`.
- `lunabee-detekt-config.yml`: `warningsAsErrors: true`; `AbsentOrWrongFileLicense` requires Apache-2.0 header on every source file.
- `compose/presenter/template/*` excluded.
- Run `./gradlew detekt -Pstudio.lunabee.detekt.skipDependencySorting` by default; drop flag when any `*.gradle*` or `*.toml` changed (AGENTS.MD). Fix warnings, never suppress.

## CI

- TeamCity project `LunabeeStudio_Android_LunabeeComposeAndroid` on `ci-android.lunabee.studio`; inspect with `teamcity` CLI. Pipeline config not stored in repo.
- Dependency updates arrive as Renovate PRs.
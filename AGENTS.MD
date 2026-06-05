# AGENTS.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository overview

LBAndroid (a.k.a. Lunabee Compose / Lunabee Android) is a multi-module Gradle library that publishes ~40+ artifacts to Maven Central under several `studio.lunabee.*` group IDs. Modules are split into:

- `compose/<name>` — Compose Android (or KMP) libraries (e.g. `core`, `theme`, `presenter`, `navigation`, `uifield/*`).
- `common/<group>/<name>` — cross-platform / Android / Kotlin libraries (`logger`, `core`, `extension`, `ktor`, `loading`, `monitoring`, `test`, `bom`).
- `demo-compose` — Android demo app exercising the Compose modules.
- `ios-demo` (+ `libsIosDemo` Xcode project) — KMP iOS demo target.

Module path → Maven group ID mapping happens in the root `build.gradle.kts` (`project.allprojects { … }`): the segments under `compose/` or `common/<group>/` become the trailing group ID after `studio.lunabee.`. The `common/` segment itself is dropped so e.g. `common/loading/loading-core` → `studio.lunabee.loading:loading-core`.

Per-module versions live as `LBC*_VERSION` / `*_VERSION` constants in `buildSrc/src/main/kotlin/AndroidConfig.kt`. The constant name must match the module name in `UPPER_CASE` with `-` → `_`. Each module's `build.gradle.kts` sets `version = AndroidConfig.<CONST>`.

## Build configuration

- **JDK 21**, Kotlin `2.3.x`, AGP `9.x`, `compileSdk 37`, `minSdk 23` (defined in `BuildConfigs.kt` and `AndroidConfig.kt`).
- Version catalog: `gradle/libs.versions.toml`.
- Two convention-plugin containers (both are used — don't merge):
    - `buildSrc/` — precompiled script plugins: `lunabee.android-library-conventions`, `lunabee.android-compose-library-conventions`, `lunabee.kmp-library-conventions`, `lunabee.kmp-android-library-conventions`, `lunabee.java-library-conventions`.
    - `build-logic/` (included build) — `lunabee.library-publish-conventions` (Maven Central via JReleaser + signing + Dokka).
- `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` is on in `settings.gradle.kts`; reference modules with `projects.foo.bar`, not string paths.
- Repositories are locked with `FAIL_ON_PROJECT_REPOS`; declare new repos in `settings.gradle.kts`.

## Commands

```bash
# Verification — required before finishing work (see AGENTS.MD).
./gradlew detekt -Pstudio.lunabee.detekt.skipDependencySorting   # default
./gradlew detekt                                                  # use this when *.gradle* or *.toml changed
# Detekt config: warningsAsErrors=true and an Apache-2.0 license header is required on every source file.

# Build / test
./gradlew assemble                          # build everything
./gradlew :compose:core:assemble            # one module
./gradlew test                              # all unit tests
./gradlew :compose:core:test                # one module
./gradlew :compose:core:allTests            # aggregated test report (declared by android-library-conventions)
./gradlew :demo-compose:installDebug        # install the demo app

# Publishing
./gradlew publishToMavenLocal               # required before validating against another local project
./gradlew publishList                       # prints publishable modules (used by CI)
./gradlew :foo:bar:PrintCoordinates         # prints group:name:version for one module
./gradlew setAllSnapshotVersion             # bulk-set snapshot version on all published modules
```

To bump the Gradle wrapper: `./gradlew wrapper --gradle-version latest` (distributionType is pinned to `ALL`).

## CI

TeamCity project: [LunabeeStudio_Android_LunabeeComposeAndroid](https://ci-android.lunabee.studio/project/LunabeeStudio_Android_LunabeeComposeAndroid). Use the `teamcity` CLI (see the `teamcity-cli` skill) to inspect builds, logs, queues, and agents.

## Workflow rules

These are project-specific, not generic dev practice:

- **Commits**: split semantic commits, one concern per commit, Conventional-Commits prefixes (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`).
- **Kotlin API**: KDoc on public functions (skip in demo apps); use named arguments for any call argument that appears on a continuation line; prefer Kotlin libraries over Java equivalents (esp. time, test).
- **Detekt**: fix warnings, don't suppress.
- **Releases**: each user-visible change updates `CHANGELOG.MD` (follow the existing format — one block per release with a fenced module/version list, then bullet points). Bump the touched module's `*_VERSION` per semver; **don't bump the same module twice on one branch** — fold further work into the existing unreleased bump. Major bumps are reserved for breaking API changes. If a module's transitive dependency moves, also bump that module's minor version.
- **Validating against another local project**: run `publishToMavenLocal` first so the consumer can resolve the artifact.

## Architecture notes worth knowing up front

- **Presenter stack** (`compose/presenter/*`): a Compose screen is driven by a `UiState`, an `Action` sealed type, a stateless `Screen`, an `LBPresenter` (or `LBSinglePresenter` / `LBFactorySinglePresenter`), and an `LBReducer` (typically `LBSingleReducer`). The reducer is the only place that mutates state, navigates (`performNavigation`), or runs side effects. Presenters expose `content`, declare `flows`, parse route args from `SavedStateHandle`, and create the reducer in `createReducer`. KSP (`presenter-ksp`) generates reducer factories; `presenter-koin-ksp` (which pulls `presenter-ksp` transitively) additionally generates the Koin bindings — `ksp { arg("studio.lunabee.presenter.generateKoinModule", "true"); arg("studio.lunabee.presenter.koinModulePackage", "<namespace>") }` controls this. Koin projects apply `ksp(presenter-koin-ksp)` only. Full guide: `compose/presenter/README.MD`; v1→v2 migration: `compose/presenter/MIGRATION_V2.MD`.
- **KMP modules** (e.g. `common/core/core`, `common/extension/extension`) target `iosArm64` + `iosSimulatorArm64` with **SKIE** (static frameworks). Publication uses `PublishType.Kmp` in `lunabee.library-publish-conventions`, with overrides that skip jar artifacts for the iOS targets (workaround for jreleaser/jreleaser#1746).
- **BOM** (`common/bom`) pins versions across all published modules and reads its version from `AndroidConfig.bomVersion()` (env override: `EnvConfig.EnvVersionName`).

## Agent skills (project-local)

The `docs/agents/` directory wires three meta-conventions used by skills:

- **Issue tracker** — markdown files at `.scratch/<feature-slug>/issues/<NN>-<slug>.md`, with a `Status:` line near the top. PRD is `.scratch/<feature-slug>/PRD.md`. (`docs/agents/issue-tracker.md`)
- **Triage labels** — five canonical states: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. Applied by editing the `Status:` line. (`docs/agents/triage-labels.md`)
- **Domain docs** — single-context: read `CONTEXT.md` and any relevant `docs/adr/*.md` before exploring. Use glossary vocabulary in titles/proposals; surface ADR conflicts explicitly. Proceed silently if these files don't exist. (`docs/agents/domain.md`)

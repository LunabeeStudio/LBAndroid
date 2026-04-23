# AGENTS

## Commit Rules

- Use split semantic commits.
- Keep each commit focused on a single concern.
- Use Conventional Commit style prefixes such as `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, or `chore:`.

## Kotlin API Rules

- Add KDoc to public functions (except in demo apps)
- When a call spans multiple lines, use named arguments for the arguments on those lines whenever the API supports it.
- Always prefer Kotlin libraries over Java libraries when Kotlin equivalents exist, such as for time and test APIs.

## Verification Rules

- Run `./gradlew detekt -Pstudio.lunabee.detekt.skipDependencySorting` before finishing work when no `*.gradle*` or `*.toml` file changed.
- Run `./gradlew detekt` before finishing work when a `*.gradle*` or `*.toml` file changed.
- Fix detekt warnings instead of ignoring them.
- When validating changes against another local project, publish the updated artifacts first with `./gradlew publishToMavenLocal` so it can be found by other project.

## Release Rules

- Fill the changelog for each feature, following the latest changelog format already used in the repository.
- Bump the touched module version according to semantic versioning.
- Do not bump the same module version twice on the same branch. When additional work lands before release, fold it into the existing unreleased branch version instead of creating another bump.
- Reserve major version bumps for large API breaking changes.
- Also bump the minor version of modules that depend on a touched module when the dependency version must move transitively.

## Common commands

```bash
./gradlew detekt -Pstudio.lunabee.detekt.skipDependencySorting   # lint (skip sort check)
./gradlew detekt                                                  # lint with dependency sort check
./gradlew :compose:core:allTests                                  # aggregated test report for one module
./gradlew test                                                    # host tests across all modules
./gradlew publishToMavenLocal                                     # publish every lib locally (use before validating in a downstream project)
./gradlew publishList                                             # print the `;`-separated list of publishable projects
./gradlew setAllSnapshotVersion                                   # rewrite all publishable projects to a snapshot version
./gradlew :demo-compose:installDebug                              # install the Android demo app
```

Running a single JVM test: `./gradlew :<module-path>:test --tests "fully.qualified.ClassName.method"`.

## Architecture

**Multi-module Gradle build** with typesafe project accessors. Three source roots registered from `settings.gradle.kts`:
- `compose/<name>` → project path `:compose:<name>` (Compose-specific libs, e.g. `:compose:core`, `:compose:theme`, `:compose:presenter:presenter`). Includes the `compose/presenter/template` scaffold which is excluded from detekt.
- `common/<...>/<name>` → project path `:<name>` (KMP-friendly libs: `:core`, `:extension`, `:ktor-core`, `:monitoring-core`, `:loading-core`, `:bom`, …). The `common/` prefix is dropped from both the Gradle path and the Maven group.
- `demo-compose` and `ios-demo` — sample apps, not published.

**Group IDs are derived from the project path** in the root `build.gradle.kts` (`project.allprojects { group = … }`): directory segments become dot-separated, with a leading `studio.lunabee` and the `common` prefix stripped. So `compose/monitoring/monitoring-ui` publishes as `studio.lunabee.compose.monitoring:monitoring-ui`, and `common/core/core-android` publishes as `studio.lunabee.core:core-android`. Don't hardcode group IDs in module `build.gradle.kts` files.

**Convention plugins** live in two includeBuilds:
- `buildSrc/` — Android/KMP/Java library conventions (`lunabee.android-library-conventions`, `lunabee.android-compose-library-conventions`, `lunabee.kmp-library-conventions`, `lunabee.kmp-android-library-conventions`, `lunabee.java-library-conventions`) and the shared `AndroidConfig` object.
- `build-logic/` — `lunabee.library-publish-conventions` wires JReleaser + `maven-publish` + Dokka. Publication type (Android / Java / KMP / BOM) is auto-detected from applied plugins.

**`AndroidConfig`** (in `buildSrc/src/main/kotlin/AndroidConfig.kt`) is the single source of truth for per-module published versions (`LBCCORE_VERSION`, `CORE_VERSION`, `MONITORING_CORE_VERSION`, `PLATFORM_VERSION`, …) and for JVM target / compile SDK. Module names map to `UPPER_SNAKE_CASE` constants here (e.g. `monitoring-core` → `MONITORING_CORE_VERSION`). When bumping a version per the release rules, update the constant in this file.

**Version catalog** is at `gradle/libs.versions.toml`. AGP 9.x, JDK/JVM 21, Kotlin plugins (Compose, Serialization, KSP), Hilt, Room, Koin, Dokka, JReleaser, and the in-house `studio.lunabee.plugin.detekt` (`lbDetekt`) are declared here. `RepositoriesMode.FAIL_ON_PROJECT_REPOS` is set — add new repos in `settings.gradle.kts`, not in modules.

**Detekt** uses `lunabee-detekt-config.yml` overlaid on the plugin's config. `warningsAsErrors: true` and `AbsentOrWrongFileLicense: true` — every Kotlin/Gradle file needs the Apache-2.0 header, so preserve it when editing and add it to new files.

**Presenter stack** (`compose/presenter/*`) is the recommended screen architecture. See `compose/presenter/README.MD` for the split: `UiState` + `Action` + stateless `Screen` + `LBSinglePresenter` (or `LBFactorySinglePresenter` when the reducer can be DI-resolved) + `LBSingleReducer`. The reducer owns state transitions, navigation (`performNavigation`), and side effects; the screen only renders. Use the `compose/presenter/template` files as a starting point. `compose/presenter/MIGRATION_V2.MD` documents the v2 generated reducer-factory migration.

**Monitoring stack** (`common/monitoring/*`) is KMP + Compose Multiplatform. `monitoring-core` defines contracts, `monitoring-room` provides the Room-backed `LBMonitoring` store, `monitoring-okhttp` and `monitoring-ktor` are client interceptors, and `monitoring-ui` renders the inspector. See `common/monitoring/README.md`.

**BOM**: `common/bom` publishes `studio.lunabee:bom`, pinned by `PLATFORM_VERSION` in `AndroidConfig`, overridable via the `ENV_VERSION_NAME` env var during CI releases.

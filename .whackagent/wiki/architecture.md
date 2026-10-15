# Architecture

Overall shape of LBAndroid: ~50 Gradle projects — layout, wiring, Maven coordinate grouping, demo apps.

Related: [[build-and-publishing]] · [[presenter]] · [[synchronization]] · [[common-libraries]] · [[compose-ui]]

## Top-level layout

| Path | Role |
|---|---|
| `compose/` | Android Compose libs (+ presenter stack). Published under `studio.lunabee.compose[.<sub>]`. |
| `common/<group>/<name>` | Cross-platform / Android / Kotlin libs, one folder per family. Published under `studio.lunabee.<group>`. |
| `common/bom` | Java-platform BOM, `studio.lunabee:bom`. |
| `demo-compose/` | Android demo app consuming most modules. |
| `demo-cli/` | Kotlin/Native CLI chronometer demo (macosArm64, linuxX64, mingwX64) using `:core`. |
| `ios-demo/` + `libsIosDemo/` | KMP target building static `iosDemo` framework, consumed by `libsIosDemo` Xcode project. |
| `buildSrc/`, `build-logic/` | Convention plugins — see [[build-and-publishing]]. |
| `tools/` | One-off scripts (e.g. `presenter_migration_1.8.0.py`). |

## How modules are included

`settings.gradle.kts` no list projects by path; uses helpers:

- `addComposeModule("presenter/presenter", …)` → project `:compose:<last segment>`, `projectDir = compose/<path>`.
- `addCommonModule("core/core-android", …)` → **flat** project `:<last segment>`, `projectDir = common/<path>`.
- `addModule(...)` direct for odd cases: `:loading-checks` lives at `common/loading/loading-compose/checks` (lint rules, shipped inside `loading-compose` via `lintPublish`).
- Demos plain `include("demo-compose", "demo-cli", "ios-demo")`; `build-logic` is `includeBuild`.

Consequences:
- Type-safe accessors (`TYPESAFE_PROJECT_ACCESSORS`) read `projects.compose.uifieldCore` vs `projects.coreAndroid` — never string paths.
- `compose/core` and `common/core/core` both named `core`; only paths differ (`:compose:core` vs `:core`).
- Repositories centralised (`FAIL_ON_PROJECT_REPOS`); JitPack allowed only for `com.github.parse-community` (Parse SDK, used by `synchronization-parse-room`).
- `compose/presenter/presenter-hilt*` folders hold only stale `build/` output — not included modules.

## Path → Maven group ID

Root `build.gradle.kts` (`project.allprojects { … }`) derives `group` from project dir: split relative path, drop leading `common`, drop last segment (project name), prefix `studio.lunabee`. artifactId = project name.

| Directory | Coordinate |
|---|---|
| `compose/core` | `studio.lunabee.compose:core` |
| `compose/presenter/presenter-ksp` | `studio.lunabee.compose.presenter:presenter-ksp` |
| `compose/test/androidtest` | `studio.lunabee.compose.test:androidtest` |
| `common/core/core-android` | `studio.lunabee.core:core-android` |
| `common/bom` | `studio.lunabee:bom` |

Move folder → published coordinate changes.

## Module groups

| Group | Kind | One line |
|---|---|---|
| `compose/{core,foundation,accessibility,haptic,image,crop,glance,theme,navigation}` | Android Compose | Reusable Compose building blocks; `theme` builds on `material-color-utilities`. See [[compose-ui]]. |
| `compose/material-color-utilities` | JVM | M3 color algorithms used by `theme`. |
| `compose/uifield/*` | Android (Compose; `uifield-form` plain Android) | Form fields, country/phone pickers, form layer. See [[compose-ui]]. |
| `compose/presenter/*` | Android Compose + JVM (annotations, KSP processors) | MVI presenter stack, generated reducer factories. See [[presenter]]. |
| `compose/test/{androidtest,robolectrictest}` | Android Compose | Test helpers for instrumented / Robolectric tests. |
| `common/logger` | KMP | Kermit-based logger (+ Crashlytics writer, KMP android+iOS). Root of most dependency chains. |
| `common/core` | KMP + Android + Compose | `core` (KMP), `core-android`, `core-compose`. See [[common-libraries]]. |
| `common/extension` | KMP + Android | Kotlin / Android extension functions. |
| `common/ktor` | KMP | Ktor client setup, JSON negotiation, Kermit logging. |
| `common/loading` | KMP core + Android Compose | Global blocking loading manager, Hilt + Koin bindings. |
| `common/monitoring` | KMP (+ Android OkHttp) | Network-call monitoring: core, Ktor/OkHttp interceptors, Room store, Compose UI. |
| `common/synchronization` | KMP (jvm core; android+iOS adapters) | Storage-agnostic sync engine + DataStore/Room/Parse adapters + events. See [[synchronization]]. |
| `common/test` | KMP + Android | `test-core` (kotlin-test helpers), `test-room`. |
| `common/bom` | java-platform | Version alignment for `common/*` families. |

## Platform split (by convention plugin applied)

- **KMP iOS + JVM + macOS/Linux/mingw**: `core`, `extension`, `logger-kermit`, `ktor-*`, `loading-core`.
- **KMP iOS + JVM**: `monitoring-core`, `monitoring-ktor`, `test-core`, `synchronization-core`.
- **KMP iOS + Android**: `logger-kermit-crashlytics`, `monitoring-room`, `monitoring-ui`, `synchronization-{core-datastore,core-room,parse-room,events}`.
- **Android library**: `core-android`, `extension-android`, `monitoring-okhttp`, `test-room`, `uifield-form`, every Compose module (`compose/*`, `core-compose`, `loading-{compose,hilt,koin}`).
- **JVM (`java-library`)**: `material-color-utilities`, `presenter-annotations`, `presenter-ksp`, `presenter-koin-ksp`, `loading-checks`.

Every KMP lib also gets `iosArm64` + `iosSimulatorArm64` static frameworks via `lunabee.kmp-library-conventions`.

## Dependency shape

Layered, leaf-to-root:

- `logger-kermit` ← `core`, `extension`, `ktor-kermit`, `compose:core`, `synchronization-events`, …
- `core` ← `core-android` ← `core-compose` ← `loading-compose` ← `loading-{hilt,koin}`; `core` ← `loading-core`, `synchronization-core`.
- `ktor-core` ← `ktor-json`, `monitoring-ktor`; `monitoring-core` ← every other `monitoring-*`.
- `synchronization-core` ← all `synchronization-*` adapters.
- `compose:core` ← `image` ← `uifield-*`, `presenter`; `presenter-annotations` ← `presenter`, `presenter-ksp` ← `presenter-koin-ksp`; `presenter` ← `presenter-koin`.

Compose modules depend on `common/*`, never reverse.

## BOM

`common/bom/build.gradle.kts` = `java-platform` declaring `constraints` on `core`, `extension`, `ktor`, `loading`, `logger`, `test`, `monitoring` families. KMP modules with JVM target also pin `<name>-jvm` artifact (`addJvmModule`). `compose/*` and `synchronization/*` **not** in BOM. Versioning: [[build-and-publishing]].

## Demo apps

- `demo-compose` — `com.android.application` with Hilt, Koin, Room, KSP (`presenter-koin-ksp`); one package per feature under `demo-compose/src/main/kotlin/studio/lunabee/compose/demo/` (core, presenter, monitoring, synchronization, uifield, …). Instrumented tests use `compose:androidtest`.
- `demo-cli` — proves KMP `core` on desktop native targets; pass args with `-Pargs="…"`.
- `ios-demo` — exports `monitoring-{core,ui,room}` into `iosDemo` framework; `libsIosDemo` (SwiftUI) runs framework's `embedAndSignAppleFrameworkForXcode` task. Note: Xcode script calls `:iosDemo:`, Gradle project is `:ios-demo`.

Demos not published (no `lunabee.library-publish-conventions`).
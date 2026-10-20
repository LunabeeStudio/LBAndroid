---
issue: 369
phase: validated
wiki: [[common-libraries]], [[build-and-publishing]], [[architecture]]
note: Requested from the RunMotion grill of LunabeeStudio/RunMotion_Android#1194 (first consumer, V0→V1 Parse session copy). Moving Kokoriko and oneSafe6 onto it is out of scope (tickets in their repos).
created: 2026-10-16
---

## Context / Decisions

Source: issue #369 body (spec excerpt from the RunMotion grill of LunabeeStudio/RunMotion_Android#1194).

Goal: a small KMP library for versioned, step-by-step app data migrations, shared by Kokoriko, oneSafe6 and RunMotion. It must let oneSafe6 move onto it without changing its behaviour: typed step input, version stored per safe, failure returned to the caller, error on a missing step.

Prior art: no existing library fits. Room `Migration(startVersion, endVersion)` has the same version-pair shape but only migrates a SQLite schema through a connection, not app data. Custom build decided.

Existing implementations read during the grill:
- Kokoriko (`Kokoriko_KMP/domain/.../domain/migration`): `AppMigration` with `startVersion`, `endVersion` and `suspend fun migrate()` that throws. `RunMigrationsUseCaseImpl` reads the version from `SettingsRepository` (`?: 0`), runs steps up to `MigrationConstant.LastVersion`, saves the version after each step, errors on a missing step, and logs then stops on failure.
- oneSafe6 (`oneSafe6_Android/app/migration`): `AppMigration0` / `AppMigration15` take `MigrationSafeData0` / `MigrationSafeData15` (`as15()`) and return `LBResult<Unit>`. `LoginAndMigrateUseCase` reads the version from the input, saves it with `safeRepository.setSafeVersion(safeId, …)`, throws `MISSING_MIGRATION` on a missing step and fails the login on a failing step. Its loop never exits on a failing step (the version stays the same and the same step is retried forever); the library stopping at the first failure fixes that.

Decisions:
- **Runner is a plain class, not a use case.** The library exposes an engine (like `LoadingManager` or `LBSyncOperator`). Each app keeps its own use case that calls it and decides what to do with a failure: Kokoriko and RunMotion log and ignore it, oneSafe6 fails the login and keeps its post-migration work (load safe, relink files).
- **Step input is generic.** `LBMigration<in Input>` with `startVersion: Int`, `endVersion: Int` and `suspend fun migrate(input: Input)`. Apps with no input use `Unit`. oneSafe6 uses `MigrationSafeData0`; its v15+ steps keep an adapter on the oneSafe6 side that calls `input.as15()`, so the v0/v15 split stays out of the library.
- **Steps throw; the runner returns `LBResult<Unit>`.** `LBMigrationRunner<Input>(migrations: List<LBMigration<Input>>, lastVersion: Int)` exposes `suspend fun run(input: Input, versionStore: LBMigrationVersionStore): LBResult<Unit>`. It catches any exception from a step, rethrows `CancellationException`, stops at the first failing step without saving that step's version, and returns a `Failure` carrying the step's throwable. oneSafe6 steps call `.getOrThrow()` on their existing results.
- **Version store is passed to `run`, not to the constructor.** The runner can be a DI singleton while oneSafe6 builds a store per safe at each login. `interface LBMigrationVersionStore { suspend fun getVersion(): Int; suspend fun setVersion(version: Int) }`. The library has no notion of "no stored version": the app maps it (Kokoriko: `?: 0`), which keeps the fresh-install choice with the app (start at 0, or return `lastVersion` to skip every step). No scope type in the library: scope lives in the app's store implementation. `run` calls `setVersion` after each successful step and never after a failed one.
- **Explicit `lastVersion`.** Constructor parameter, matching the `MigrationConstant.LastVersion` both apps keep. A stored version at or above `lastVersion` returns `Success` without running anything. No step starting at the current version returns a `Failure` carrying the public `LBMissingMigrationException(fromVersion)`. The constructor `require`s unique `startVersion`s and `endVersion > startVersion` for every step, so a bad list fails fast instead of looping.
- **Runner logs through `LBLogger`.** Info on each successful step (`Migration from 3 to 4 succeeded`), error with the throwable on the failing step.
- **Module `migration-core`.** `common/migration/migration-core` → project `:migration-core`, artifact `studio.lunabee.migration:migration-core`, `MIGRATION_CORE_VERSION = "1.0.0"`, package `studio.lunabee.migration`. Plugin `lunabee.kmp-library-conventions` with the same targets as `core` / `loading-core` (`jvm()`, iOS from the convention, `macosArm64`, `linuxX64`, `mingwX64`). Depends on `core` (for `LBResult` and `LBLogger`) and coroutines. Four public types in the package root: `LBMigration`, `LBMigrationRunner`, `LBMigrationVersionStore`, `LBMissingMigrationException`.
- **DataStore store in a sibling module `migration-datastore`.** `common/migration/migration-datastore` → artifact `studio.lunabee.migration:migration-datastore`, `MIGRATION_DATASTORE_VERSION = MIGRATION_CORE_VERSION`, following `synchronization-core-datastore`, so `migration-core` stays free of DataStore (oneSafe6 stores its version in Room). One class: `DataStoreMigrationVersionStore(dataStore: DataStore<Preferences>, key: String = "lbMigrationVersion", versionWhenAbsent: Int = 0)`. The app passes its own DataStore: no extra file, no Android `Context` provider. `key` allows several scopes in one store. Plugin `lunabee.kmp-library-conventions` with `jvm()` and the targets `datastore-preferences-core` supports; `api(datastore-preferences-core)` and `api(migration-core)`.
- **Release.** Both modules registered in `settings.gradle.kts` (`addCommonModule`) and in the BOM (`addJvmModule`). BOM 4.16.0 is released, so `PLATFORM_VERSION` goes to `4.17.0`. One `CHANGELOG.MD` block lists `Migration Core 1.0.0`, `Migration Datastore 1.0.0` and `bom 4.17.0`. The root README artifact list gains both modules, and a short `common/migration/README.md` documents usage.
- **Out of scope (YAGNI).** Moving Kokoriko and oneSafe6 onto the library (separate tickets in their repos), a demo screen (`loading-core` has none), a concurrency guard on `run` (each app calls it once, at start or at login).

## Acceptance criteria

- `./gradlew :migration-core:assemble :migration-datastore:assemble` succeeds, and `./gradlew :migration-core:PrintCoordinates` prints `studio.lunabee.migration:migration-core:1.0.0` (`migration-datastore` prints `studio.lunabee.migration:migration-datastore:1.0.0`).
- With a store at version 0, `lastVersion = 2` and steps 0→1 and 1→2, `run` calls both steps in order, calls `setVersion(1)` then `setVersion(2)`, and returns `Success`.
- With a store at version 2 and `lastVersion = 2`, `run` calls no step, never calls `setVersion`, and returns `Success`.
- When step 1→2 throws, `run` returns a `Failure` carrying that exception, the store holds 1, and no later step runs. A second `run` retries step 1→2.
- When a step throws `CancellationException`, `run` rethrows it instead of returning a `Failure`.
- With steps 0→1 and 2→3 and `lastVersion = 3`, `run` from 0 runs 0→1, then returns a `Failure` carrying `LBMissingMigrationException` with `fromVersion = 1`, and the store holds 1.
- Building `LBMigrationRunner` with two steps sharing a `startVersion`, or with a step whose `endVersion <= startVersion`, throws `IllegalArgumentException`.
- The `input` passed to `run` is the value every step receives in `migrate`.
- `DataStoreMigrationVersionStore` returns `versionWhenAbsent` when its key is absent, returns the saved value after `setVersion`, and two instances with different `key`s on one DataStore keep separate versions (covered by `commonTest` on a temp-file DataStore).
- The BOM resolves both new artifacts, `CHANGELOG.MD` has the `Migration Core 1.0.0` / `Migration Datastore 1.0.0` / `bom 4.17.0` block, and `./gradlew detekt` passes.

## Implementation

Brick 1: `migration-core`
- `common/migration/migration-core/` (new module, `MIGRATION_CORE_VERSION = "1.0.0"`, registered in `settings.gradle.kts`).
- `LBMigration`, `LBMigrationRunner`, `LBMigrationVersionStore` and `LBMissingMigrationException` are in `studio.lunabee.migration`.
- `run` has a single return to satisfy detekt `ReturnCount`. A private `runStep` returns the step's throwable and rethrows `CancellationException`.
- Targets: jvm, iOS (from the convention), macosArm64, linuxX64 and mingwX64.
- Tests: `LBMigrationRunnerTest` in commonTest has 8 tests, one per runner criterion. The duplicate start version and the bad end version are tested separately.

Brick 2: `migration-datastore`
- `common/migration/migration-datastore/` (new module, `MIGRATION_DATASTORE_VERSION = MIGRATION_CORE_VERSION`).
- `DataStoreMigrationVersionStore` is in `studio.lunabee.migration.datastore` and uses `intPreferencesKey(key)`.
- Targets: jvm, iOS, macosArm64 and linuxX64. mingwX64 is dropped because `datastore-preferences-core` 1.2.1 publishes no `mingw_x64` variant.
- Tests: `DataStoreMigrationVersionStoreTest` in commonTest has 4 tests on a temp-file DataStore.

Brick 3: release wiring
- The BOM declares `addJvmModule` for both modules, and `PLATFORM_VERSION` is `4.17.0`.
- `CHANGELOG.MD` has a `2026-10-16` block, the root `README.md` has two artifact rows, and `common/migration/README.md` documents usage.

Proof
- `./gradlew :migration-core:assemble :migration-core:allTests` passes: 8 tests on each of jvm, macosArm64 and iosSimulatorArm64.
- `./gradlew :migration-datastore:assemble :migration-datastore:allTests` passes: 4 tests on each of jvm, macosArm64 and iosSimulatorArm64.
- `PrintCoordinates` prints `studio.lunabee.migration:migration-core:1.0.0` and `studio.lunabee.migration:migration-datastore:1.0.0`.
- `:bom:generatePomFileForBomPublication`: the BOM is version 4.17.0 and constrains `migration-core:1.0.0` and `migration-datastore:1.0.0`.
- Plain `./gradlew detekt` passes.

Pre-existing, out of scope: every `addJvmModule` `-jvm` constraint in the generated BOM pom has version `unspecified`. This includes `loading-core-jvm`, and now `migration-core-jvm` and `migration-datastore-jvm`.

## Review

### validation (2026-10-16)

- Tools: plain `./gradlew detekt` passes.
- Verifier pass 1 covered style, elegance, structure and correctness, and returned 5 findings:
  - [major] [structure] `migration-core` used `implementation(projects.core)`, but `run` returns `LBResult`, so `core` was runtime-only in the published metadata. Fixed: it is now `api(projects.core)`, and the pom shows `core-jvm` at compile scope.
  - [minor] [correctness] A store error from `getVersion` or `setVersion` escaped `run` instead of coming back as a `Failure`. Fixed: one try now wraps the loop, returns `Failure(e)` for any error and still rethrows `CancellationException`. Two new tests cover it (`getVersion` throws, `setVersion` throws).
  - [nit] The missing-step failure was not logged. Fixed: it goes through the shared error log.
  - [nit] The logger tag was a hard-coded string. Fixed: it is now `LBLogger.get<LBMigrationRunner<*>>()`.
  - [nit] Test calls on continuation lines used positional arguments. Fixed: they use named arguments.
- Verifier pass 2 found one new minor issue: the shared catch logged a generic "Migration failed", so the log no longer named the failing step. Fixed with a step marker.
- Verifier pass 3 found that the marker was still set during `setVersion`, so a store write error blamed the step (minor), and that the name and type were misleading (nit). Fixed: `runningStep: LBMigration<Input>?` is cleared before `setVersion`. A step failure logs `Migration from X to Y failed`, and a store or missing-step failure logs `Migration failed`.
- Verifier pass 4: clean, with all four lenses run.
- Docs: `common/migration/README.md` now says that a version store error also comes back as a `Failure`.
- Autofix changed `migration-core/build.gradle.kts`, `LBMigrationRunner.kt`, `FakeMigrationVersionStore.kt` and `LBMigrationRunnerTest.kt` after the user's test.

## Verification

This is a library (`verify.mode: off`), so build, tests and detekt are the whole proof. The agent did not drive anything at runtime.

After the validation autofix, `./gradlew :migration-core:allTests :migration-datastore:allTests` and plain `./gradlew detekt` pass again. Store-failure behaviour has 2 new tests, so the runner has 10 tests.

## Feedback

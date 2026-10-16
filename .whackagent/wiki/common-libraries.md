# Common Libraries

Non-UI building blocks under `common/` (published as `studio.lunabee.<group>:<module>`): results, logging, global loading, app data migrations, Ktor setup, network monitoring, test helpers.

Out of scope: [[synchronization]] and BOM (see [[build-and-publishing]]). Overall module layout: [[architecture]].

## Platform at a glance

Convention plugin in each `build.gradle.kts` decides platform:

| Convention plugin | Targets | Modules |
|---|---|---|
| `lunabee.kmp-library-conventions` + `jvm()` in module | JVM + iosArm64 + iosSimulatorArm64 (SKIE static frameworks) | `core`, `extension`, `ktor-*`, `loading-core`, `logger-kermit`, `migration-core`, `migration-datastore`, `monitoring-core`, `monitoring-ktor`, `test-core` |
| `lunabee.kmp-android-library-conventions` | Android + iOS | `logger-kermit-crashlytics`, `monitoring-room`, `monitoring-ui` (Compose Multiplatform) |
| `lunabee.android-library-conventions` | Android | `core-android`, `extension-android`, `monitoring-okhttp`, `test-room` |
| `lunabee.android-compose-library-conventions` | Android + Compose | `core-compose`, `loading-compose`, `loading-hilt`, `loading-koin` |

- "KMP" modules ship **JVM** target (not Android target); Android apps consume JVM artifact.
- Gradle paths flat: `common/loading/loading-core` is `:loading-core` → `projects.loadingCore` (see `addCommonModule` in `settings.gradle.kts`).
- Versions shared per group in `buildSrc/src/main/kotlin/AndroidConfig.kt`: e.g. `CORE_VERSION` covers all three core modules, `LOADING_VERSION` all four loading modules, `KTOR_VERSION` all three ktor modules, `MONITORING_*_VERSION` all alias `MONITORING_CORE_VERSION`.

## Dependency graph

```
logger-kermit  (api: Kermit)
  ├─ core (api) ── core-android (api) ── core-compose (api)
  ├─ extension (api) ── extension-android
  ├─ ktor-kermit
  └─ monitoring-okhttp, compose:core
core ── loading-core ── loading-compose ── loading-hilt / loading-koin (+ core-compose)
core (api) ── migration-core (api) ── migration-datastore (+ api: datastore-preferences-core)
ktor-core ── ktor-json, monitoring-ktor
monitoring-core ── monitoring-ktor / -okhttp / -room / -ui
```

`logger-kermit` root of almost everything; `core` re-exports it, so depend on `core` → get `LBLogger`.

## logger — `logger-kermit`, `logger-kermit-crashlytics`

Purpose: one logging façade (Touchlab Kermit) for all Lunabee libs and apps.

- `LBLogger.get<T>()` / `LBLogger.get(name)` — tag from class simple name (or FQN with `short = false`). `common/logger/logger-kermit/src/commonMain/kotlin/studio/lunabee/logger/LBLogger.kt`
- `Logger.e(Throwable)` common extension; Timber-like `v/d/i/w/e(message, vararg args)` format overloads **JVM-only** (`src/jvmMain/.../LoggerJvmExt.kt`).
- `LBCrashlyticsLogWriter` (`logger-kermit-crashlytics/src/commonMain/.../LBCrashlyticsLogWriter.kt`) — Kermit `LogWriter` backed by kermit-crashlytics:
  - Logs only **above Debug** (`isLoggable` = `severity > Debug`).
  - Log with throwable → recorded as handled exception wrapped in `LogWrapperException` (keeps severity/tag in message).
  - Error without throwable → also handled exception when `recordErrorMessage` (default `true`); else breadcrumb `logMessage`.
  - `exceptionListToIgnore` and `shouldLog` filter before send.

## core — `core`, `core-android`, `core-compose`

Purpose: shared result types, loading-delay logic, small Android/Compose helpers.

| Type | Module | Path (under `common/core/`) |
|---|---|---|
| `LBResult<T>` (`Success` / `Failure`) | core | `core/src/commonMain/kotlin/studio/lunabee/core/model/LBResult.kt` |
| `LBFlowResult<T>` (`Loading` / `Success` / `Failure`) + `transformResult`, `mapResult`, `unit()` | core | `core/src/commonMain/kotlin/studio/lunabee/core/model/LBFlowResult.kt` |
| `LBLoadingVisibilityDelayDelegate` | core | `core/src/commonMain/kotlin/studio/lunabee/core/helper/LBLoadingVisibilityDelayDelegate.kt` |
| `LBLifecycleApplication`, `LBLifecycleListener`, `LifecycleScope`, `AbsentLiveData`, `NetworkUtils` | core-android | `core-android/src/main/kotlin/studio/lunabee/core/...` |
| `rememberShowDelayedLoading`, `LBDimens` | core-compose | `core-compose/src/main/kotlin/studio/lunabee/core/...` |

Rules worth knowing:
- `Failure` carries optional `failureData`, so failure can still hold stale data; `data` returns whichever payload state has.
- `LBFlowResult.asResult()` **throws** on `Loading`; `LBResult.getOrThrow()` throws failure's throwable (or generic `Exception`).
- Default `mapResult`/`transformResult` lambdas are function references — deliberate workaround for KT-60506 / KT-45505 (suspend default lambdas can't inline).
- Anti-blink loading: `LBLoadingVisibilityDelayDelegate` waits `delayBeforeShow` (500 ms) before show, keeps loading visible at least `minLoadingShowDuration` (500 ms). Hide request before show fires cancels show entirely. Runs on `Dispatchers.Main`.
- `LBLifecycleApplication.registerLifecycle()` hooks `ProcessLifecycleOwner` for app foreground/background callbacks.

## extension — `extension`, `extension-android`

Purpose: grab-bag of Kotlin extensions, no business logic.

- `extension` (commonMain): `Flow.mapValues`, `Flow.withPreviousState`, `String.remove`, `joinToStringNotNull`, `Instant.truncateTo`, `enumValueOfOrNull` / `enumValueOfOrElse` (logs on miss), `lazyFast` (`LazyThreadSafetyMode.NONE`). JVM-only: `removeAccents`, `transformForSearch`, `titlecaseFirstChar`, `Double.formatPrice`.
- `extension-android`: View-system era helpers (Context, SharedPreferences, Fragment, Activity, Intent, Bundle, Parcel, View/TextView/Menu/AppBarLayout, `TouchableSpan`/`LinkTouchMovementMethod`, `PendingIntentCompat`). Path: `common/extension/extension-android/src/main/kotlin/studio/lunabee/extension/`.

## loading — `loading-core`, `loading-compose`, `loading-hilt`, `loading-koin`

Purpose: app-wide **global loading / interaction-blocking** state driven from anywhere (use cases, reducers), rendered once at root.

- `GlobalLoadingState` = `None` / `Blocking` (block input, no spinner) / `Loading` (block + spinner). `LoadingManager` exposes it as `StateFlow` with `startLoading/stopLoading/startBlocking/stopBlocking`, plus `withLoading {}` / `withBlocking {}` (try/finally, `yield()` right after start so UI refreshes). `common/loading/loading-core/src/commonMain/kotlin/studio/lunabee/loading/LoadingManager.kt`
- `Flow<LBFlowResult<T>>.withLoading(manager)` / `.withBlocking(manager)` start on each `Loading` emission, always stop `onCompletion` (incl. error/cancel — covered by `LoadingManagerTest`).
- Implementations: `SimpleLoadingManager` (immediate), `DelayedLoadingManager` (`Blocking` instantly, `Loading` only after anti-blink delay via `LBLoadingVisibilityDelayDelegate`).
- Stopping blocking never clears active loading state.
- `loading-compose`: `LoadingView` (fade-animated spinner over scrim, blocking slot, back press blocked by default), `LoadingBlockBackView`, `LoadingBackHandler`, `DefaultLoadingContent` (context parameter on `LoadingViewScope`). `common/loading/loading-compose/src/main/kotlin/studio/lunabee/loading/LoadingView.kt`
- **Lint rule** `ByPassLoadingHandler` (`common/loading/loading-compose/checks/.../LoadingBackHandlerDetector.kt`, shipped via `lintPublish`): warns on `androidx.activity.compose.BackHandler` with quick-fix to `LoadingBackHandler` — plain `BackHandler` added after `LoadingView` would bypass global loading.
- `loading-hilt` / `loading-koin`: same composables without `loadingManager` param — resolved via Hilt `LoadingManagerEntryPoint` (`rememberLoadingManager()`) or `koinInject()`. Neither module binds `LoadingManager`; app must provide singleton.

## migration — `migration-core`, `migration-datastore`

Purpose: versioned, step-by-step **app data** migrations (not SQLite schema — Room `Migration` does that), shared by Kokoriko, oneSafe6, RunMotion (first consumer, RunMotion_Android#1194). See `common/migration/README.md`. Task #369.

- Engine, not use case: `LBMigrationRunner<Input>(migrations, lastVersion)` — app keeps own use case calling `run(input, versionStore): LBResult<Unit>`, decides what failure means (Kokoriko/RunMotion log + ignore, oneSafe6 fails login).
- `LBMigration<in Input>`: `startVersion`, `endVersion`, `suspend migrate(input)` — steps **throw**. `Unit` input when steps need nothing; oneSafe6 v0/v15 input split stays app-side (adapter).
- `run`: reads version, runs step starting there, `setVersion(endVersion)` after each success, stops at first failure. **Any** error (step, store `getVersion`/`setVersion`, missing step) → `Failure(throwable)`; failed step's version never saved → next run retries. `CancellationException` rethrown. Version ≥ `lastVersion` → `Success`, nothing run.
- No step at current version → `Failure(LBMissingMigrationException(fromVersion))`. Constructor `require`s unique `startVersion`s, `endVersion > startVersion` → bad list fails fast, never loops.
- `LBMigrationVersionStore` passed to `run`, not constructor → runner = DI singleton, oneSafe6 builds store per safe. No "absent" notion in library: store maps it (fresh install → 0, or `lastVersion` to skip all).
- Logs via `LBLogger`: info per step, error with throwable (`Migration from X to Y failed`, or `Migration failed` for store/missing-step).
- `migration-core` exposes `core` as `api` (`LBResult` in public API).
- `migration-datastore`: `DataStoreMigrationVersionStore(dataStore, key = "lbMigrationVersion", versionWhenAbsent = 0)` over app's own `DataStore<Preferences>` (no file, no `Context`). One `key` per scope. No mingwX64 (datastore-preferences-core has none). Separate module so `migration-core` stays DataStore-free (oneSafe6 stores version in Room).
- No concurrency guard on `run` (YAGNI: called once at start / login).

## ktor — `ktor-core`, `ktor-json`, `ktor-kermit`

Purpose: opinionated Ktor `HttpClient` plugins (see `common/ktor/README.md`).

- `LBKtorExceptionHandler` + sealed `LBKtorException` (`Decoding`, `IO`, `ServerResponse`, `Unexpected`) in `common/ktor/ktor-core/src/commonMain/kotlin/studio/lunabee/ktor/LBKtorException.kt`. Optional `mapErr` converts to app error type.
  - Any **non-2xx** response throws `ServerResponse` before body decoding (avoids misleading deserialization errors).
  - Cancellation exceptions and already-wrapped `LBKtorException`s rethrown untouched.
  - Only `IOException` caught on send; serialization error while *sending* = developer bug, propagates.
- `LBKtorJson` (`ktor-json`): installs `ContentNegotiation` (kotlinx-json, lenient, ignore unknown keys, no explicit nulls by default) and `DefaultRequest` with JSON content type. `url` **mandatory** (`IllegalArgumentException` otherwise).
- `LBKtorKermit` (`ktor-kermit`): Ktor `Logging` routed to `LBLogger` at debug level, `LogLevel.All` by default.

## monitoring — `monitoring-core`, `-ktor`, `-okhttp`, `-room`, `-ui`

Purpose: record HTTP traffic locally, browse in in-app network inspector (see `common/monitoring/README.md`).

- Contract: `LBMonitoring` (paging, flows, upsert, flush, count) and models `LBRequest` / `LBPayload` in `common/monitoring/monitoring-core/src/commonMain/kotlin/studio/lunabee/monitoring/core/`. `androidx.paging` common exposed as `api`.
- Producers: `LBKtorMonitoring` Ktor plugin (`monitoring` config mandatory) and `OkHttpMonitoringInterceptor`. Both **upsert twice**: on send with `statusCode = -1`, then with response, status, duration. Bodies pretty-printed when JSON, raw otherwise.
- Storage: `LBRoomMonitoring.get(context, dispatcher)` (Android) / `get()` (iOS) returns singleton backed by own Room DB `studio.lunabee.monitoring.db`.
- UI: `LBUiMonitoring.init(monitoring)` once, then `LBMonitoringMainRoute(closeMonitoring)` — self-contained NavHost (request list → detail) with own Material 3 light/dark scheme.
- **Isolation rule**: `monitoring-room` and `monitoring-ui` each run private `koinApplication` (`RoomMonitoringIsolatedContext`, `UiMonitoringIsolatedContext` + `KoinIsolatedContext`), never touch host app's Koin graph. Forget `LBUiMonitoring.init` → fails with "Did you call start()?".

## test — `test-core`, `test-room`

- `test-core` (KMP, `api(kotlin-test)`): `assertNot {}` — passes when block fails assertion. `common/test/test-core/src/commonMain/kotlin/studio/lunabee/test/Asserts.kt`
- `test-room` (Android): `GlobalRoomMigrationTestHelper` wraps Room's `MigrationTestHelper`:
  - `emptyDBFirstToLastMigration()` — v1 → last version.
  - `filledDBAllVersionsMigration()` — for every start version, inserts one auto-generated row per table (foreign keys off), migrates to last; catches migrations that only work on empty DBs.

## Where they're exercised

`demo-compose` uses `core-android`, all `ktor-*`, `logger-kermit`, monitoring stack (`demo/monitoring/KtorDemoScreen.kt` installs all four Ktor plugins; `NetworkLogActivity` hosts `LBMonitoringMainRoute`). See [[compose-ui]] (`compose:core` itself depends on `logger-kermit`). `loading-*` and `test-*` have no demo screen.
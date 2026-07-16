# common/synchronization/

Published artifacts under `studio.lunabee.synchronization`. Root `AGENTS.MD` rules (detekt, commits,
changelog, KDoc, named-args, versioning) apply here — this file only adds what's specific to these
modules.

| Module | Path | Type | Namespace | Version const | Has README |
|---|---|---|---|---|---|
| `:synchronization-core` | `synchronization-core/` | KMP android-library (`commonMain`+`androidMain`) | `studio.lunabee.synchronization` | `SYNCHRONIZATION_CORE_VERSION` | no — documented below |
| `:synchronization-core-datastore` | `synchronization-core-datastore/` | KMP (`commonMain`+`androidMain`+`iosMain`) | `studio.lunabee.synchronization.datastore` | `SYNCHRONIZATION_CORE_DATASTORE_VERSION` | no — documented below |
| `:synchronization-core-room` | `synchronization-core-room/` | KMP + Room/KSP (`commonMain`+`androidMain`+`iosMain`) | `studio.lunabee.synchronization.room` | `SYNCHRONIZATION_CORE_ROOM_VERSION` | no — documented below |
| `:synchronization-parse-room` | `synchronization-parse-room/` | KMP android-library (`commonMain`+`androidMain`) | `studio.lunabee.synchronization.parseroom` | `SYNCHRONIZATION_PARSE_ROOM_VERSION` | **yes — read it first** |

The engine (`:synchronization-core`) is **storage-agnostic**: it persists sync cursors through the
`SyncTimestampStore` interface and never constructs a backend. A backend module provides the concrete
store; the app installs it once via `LBSyncStorage.install(...)` (see "Cursor storage" below).
`:synchronization-core-datastore` is the DataStore backend (preserves the legacy on-disk cursor file);
`:synchronization-core-room` is the Room backend (standalone DB, monitoring-room pattern). Type-safe
accessors: `projects.synchronizationCore`, `projects.synchronizationCoreDatastore`,
`projects.synchronizationCoreRoom`, `projects.synchronizationParseRoom`.

`:synchronization-parse-room` is a Parse↔Room implementation layered on `:synchronization-core`
(storage-agnostic — its managers use the no-store `LBSyncManager(logging)` constructor, so the consumer
picks the backend). Its `README.md` is the source of truth for that module (source-set split, the
BaseDao `@Upsert` trick, why no KSP lives there, the `api`-vs-`implementation` leakage rules). Don't
duplicate it here — read it before touching that module.

Both modules were **moved from `LunabeeStudio/Libraries_Android`** (commits 17d6452, d165c26), so the
code predates this repo's conventions and version lineage (the migration shim mentions "3.8.0" though
the artifact is at 2.0.0 here).

## The `:synchronization-core` engine

Generic sync framework. **Source-set split**: the whole engine (`LBSyncManager`, `LBSyncGroup`,
`LBSyncOperator`, connectivity, lifecycle) lives in `androidMain`; `commonMain` holds the pure
`runner/SyncRunner`, the framework-agnostic `store/SyncTimestampStore` **interface** (`SyncKey` keys,
`kotlin.time.Instant` dates; the DataStore backend persists epoch-millis under the key scheme
`"${syncKey}lastSyncDate"` / `…_localDate`), and the `store/LBSyncStorage` registry. No backend
(DataStore/Room) is referenced from core.

Async primitive is **Kotlin coroutines/Flow** — no Bolts `Task`, no `GlobalScope`, no completion
callbacks (those were purged in the `feature/lbsync` 2.0.0 rewrite; Bolts now only exists transitively
inside the Parse SDK). Each level has ONE suspend entry point returning `LBResult<Unit>`:
`LBSyncManager.synchronize()`, `LBSyncGroup.syncManagers()`, `LBSyncOperator.syncAllManagers()`.
Detached-from-caller execution (receiver-triggered syncs, automatic retry) runs in an injected,
library-owned `CoroutineScope` — the no-store constructor defaults it to the shared internal
`defaultSyncScope` (`CoroutineScope(SupervisorJob() + Dispatchers.IO)`). The single-flight
collapse-and-join + failure-retry machinery is extracted into the `SyncRunner` deep module in
`commonMain` (`runner/SyncRunner.kt`), unit-tested in isolation with virtual time. All four modules
declare `minSdk 24` (`AndroidConfig.SynchronizationMinSdk`) — the connectivity engine relies on
`registerDefaultNetworkCallback` (API 24) — while the rest of the repo stays at `minSdk 23`.

### Cursor storage (pluggable backend)

`SyncTimestampStore` (commonMain interface) is the whole storage contract: `suspend`
`lastServerSyncDate` / `lastSuccessfulSyncDate` / `saveSyncDates` / `clear` / `clearAll`. Keys are the
`SyncKey` value class (`LBSyncManager.syncKey: SyncKey`), dates are `kotlin.time.Instant`, non-null-write
semantics (a `null` argument leaves that cursor unchanged). `statusByKey()` on group/operator is keyed by
`SyncKey`. **Only cursors are persisted — status is derived**, not stored.

Wiring is a **one-line install** at startup — there is no automatic classpath wiring (the KMP Android
library format merges no component manifest, and iOS has no classpath init, so App-Startup-style
self-registration is impossible here):

```kotlin
// Android, DataStore backend (preserves the legacy cursor file com.lunabee.lbsynchronization)
LBSyncStorage.install(context.dataStoreSyncTimestampStore())
// Android, Room backend
LBSyncStorage.install(context.roomSyncTimestampStore())
// iOS: dataStoreSyncTimestampStore() / roomSyncTimestampStore() (no Context)
```

`LBSyncManager`'s no-store constructor (`LBSyncManager(logging)`) reads `LBSyncStorage.requireStore()`
**lazily** (on first cursor access), so install only has to run before the first sync, not before
managers are created. `requireStore()` throws with a "add a backend / call install" message if none was
installed. The `internal` primary constructor `(providedTimestampStore, scope, logging)` injects a store
directly for tests/DI. There is **no `Context` constructor anymore** (removed), and
`LBSyncOperator.resetAllTimestamps()` takes **no `Context`** (uses the installed store).

Backends: `:synchronization-core-datastore` (`DataStoreSyncTimestampStore` over a process-wide
`preferencesDataStore` delegate, file base name `SyncDataStoreName` = `com.lunabee.lbsynchronization`
so existing installs keep their cursors); `:synchronization-core-room` (standalone
`@Database`/`@Entity`/`@Dao`, `saveSyncDates` = `INSERT OR IGNORE` + `UPDATE … COALESCE` so a null arg
preserves the stored cursor). The Room backend starts with a fresh table, so switching a
DataStore-based app to Room **re-syncs once**. Every factory returns a process-wide single instance
(safe to call repeatedly; later calls ignore the parameters). The Room factories default to
`BundledSQLiteDriver` (own SQLite, version-stable) but accept any `SQLiteDriver` —
`roomSyncTimestampStore(driver = AndroidSQLiteDriver())` to use the platform SQLite instead — and
leave Room's default query context unless a `dispatcher` is passed.

Because the read is I/O, the **read/reset API is `suspend`**: `lastSuccessfulSyncDate()`,
`resetTimeStamp()`, `LBSyncOperator.resetAllTimestamps()`. Status is **not seeded in the constructor** —
call `suspend LBSyncManager.load()` (or `LBSyncOperator.loadAllStatuses()` for every managed manager)
once at startup; until then status is `NeverSync`.

The old SharedPreferences default-prefs migration is gone, so an app upgrading from the
`Libraries_Android` `lb-synchronization` **re-syncs once** (the old cursor file is not read).

Three layers, top to bottom:

- **`LBSyncOperator`** (object/singleton) — app-wide registry. Holds `groups: LinkedHashMap<String,
  LBSyncGroup>`. `initNetworkListener(context)` collects `LBConnectivityManager.networkStates` (modern
  `NetworkCallback` flow) to trigger `InternetIsBack`; `initAppLifecycleListener()` (no `Context`)
  observes `ProcessLifecycleOwner` via a `DefaultLifecycleObserver`-backed flow to trigger
  `AppForeground` and start/stop server-notification listeners (e.g. Parse LiveQuery) on
  foreground/background. There is no broadcast bridge anymore — `LBSyncApplication` was removed; apps
  just call these two init methods at startup. `syncManager<T>()` finds a registered manager by type.
- **`LBSyncGroup`** — managers in the **same group sync in parallel** (`async`/`awaitAll` over their
  `LBResult`s; a failing sibling never cancels the others — `whenAll` parity); the **operator runs
  groups sequentially**. So model table dependencies by putting the dependency in an earlier group. A
  single `var isEnabled: suspend () -> Boolean` gates a whole group (e.g. only when logged in),
  evaluated once per attempt — a blocked group sets its managers to `Disabled` and fails with
  `LBSyncClosureException`. `refreshEvents` carry a per-event min-delay debounce (`Duration`).
- **`LBSyncManager<ServerData, LocalData, PageInfo>`** — abstract per-entity engine. Pipeline is
  download → upload (then re-download unless `supportChangeNotificationFromServer()`). The subclass SPI
  is **suspend + throw-based**: `fetchRequest(...)` returns a `FetchPage`, `pushObjectsToServer(...)`,
  and `start`/`stopServerNotificationListener(): Boolean`; errors are thrown and the engine maps them to
  the `*WithError` statuses at the pipeline boundary. Override the `open` hooks for paging
  (`queryPageSize`/`hasNextPage`), incremental sync (`supportIncrementalSync`), and server push
  notifications. The `internal` primary constructor injects a `SyncTimestampStore` + `CoroutineScope`
  (used by JVM host tests with fakes + a `TestScope`); the public no-store `LBSyncManager(logging)`
  constructor resolves the installed backend lazily via `LBSyncStorage` (there is no `Context`
  constructor). Typealiases: `LBGenericSyncManager = <*,*,*>`, `LBDefaultSyncManager<S,L> = <S,L,Nothing>`.

Status & observation: `LBSyncProcessStatus` (sealed, immutable, `kotlin.time.Instant`-based) is exposed
as `LBSyncManager.status: StateFlow<LBSyncProcessStatus>` (collect it; `currentSyncStatus` is a
read-only alias for `status.value`). `LBSyncGroup`/`LBSyncOperator` add a combined
`statusByKey(): Flow<Map<String, LBSyncProcessStatus>>` and `isSyncing(): Flow<Boolean>` (snapshot of
the registry at collection time; KDoc spells out the snapshot + `syncKey`-collision caveats). Multiple
failures aggregate into `LBSyncAggregateException`. App foreground/background is observed directly from
`ProcessLifecycleOwner` by the operator (no custom `Application` needed).

### Sharp edges

- **`isProcessing()` returns `true` for `UploadFinishSuccessfully` / `DownloadFinishSuccessfully`** —
  they're mid-pipeline steps, not terminal. Only `Sync*`/`NeverSync`/`Disabled`/`*WithError` are done.
- **Per-manager cursor keys default to the class simple name** via `open val syncKey`
  (`"${syncKey}lastSyncDate"`), persisted by the installed backend (DataStore file
  `com.lunabee.lbsynchronization`, or the Room `sync_timestamp` table).
  **Renaming a `SyncManager` subclass silently resets its incremental-sync cursor** unless you pin a
  stable `syncKey` — the escape hatch is to **override `syncKey`** so the persisted key survives the
  rename. Treat `syncKey` as a persisted key.
- `currentSyncStatus` is a **read-only alias** for `status.value`; only the engine mutates state (via
  the `internal setStatusInternal`). Never try to set it from a consumer — collect `status` instead.
- **Incremental sync requires `fetchRequest` results ordered by ascending `updatedAt`** — the cursor
  saves the max instant seen, so out-of-order results lose records.
- A failed run is retried automatically by `SyncRunner` after `retryTempo` (a `Duration?`, default 30 s;
  `null` disables retry). `cancelAllRequests()` cancels the in-flight run **and** any pending retry, and
  surfaces the legacy terminal status `DownloadFinishSuccessfully`.
- Concurrent `synchronize()` calls **collapse into a single follow-up run** whose real `LBResult` every
  caller receives — the old immediate-success-while-dirty behavior is gone.
- `StateFlow` is conflated (status is state, not an event stream) and observer threading is the
  collector's choice — the old synchronous-background-thread closure sharp edge no longer applies.

## Changelog

`synchronization-core/CHANGELOG.MD` is **frozen legacy** (header literally says "Deprecated, please
update the main Changelog"). Per root `AGENTS.MD`, user-visible changes go in the **root**
`CHANGELOG.MD`; bump the touched module's `*_VERSION` in `buildSrc/.../AndroidConfig.kt`. Reference
modules with type-safe accessors: `projects.synchronizationCore`, `projects.synchronizationCoreDatastore`,
`projects.synchronizationCoreRoom`, `projects.synchronizationParseRoom`.

## Build & verify

Standard repo flow (see root `AGENTS.MD`). Quick reference:

```bash
./gradlew :synchronization-core:assemble :synchronization-core-datastore:assemble \
  :synchronization-core-room:assemble :synchronization-parse-room:assemble
./gradlew :synchronization-core:testAndroidHostTest          # engine tests (commonTest + androidHostTest) on the JVM host
./gradlew :synchronization-core-datastore:testAndroidHostTest # DataStore round-trip tests on the JVM host
./gradlew detekt -Pstudio.lunabee.detekt.skipDependencySorting   # drop the flag if *.gradle*/*.toml changed
```

Engine unit tests (`SyncRunner`, manager pipeline, group, operator, combined flows) live in
`commonTest` (the pure `SyncRunner`) and `androidHostTest` (the `androidMain` engine), run via the
`withHostTest {}` setup with `runTest` + virtual time and an in-memory `SyncTimestampStore` fake — no
device needed. The DataStore backend has its own round-trip tests; the Room backend has none (no
context-free host DB builder, matching `monitoring-room`) — it shares the `SyncTimestampStore` contract.

The backend + parse-room modules opt into `kotlin.time.ExperimentalTime` where needed and each keep a
distinct android namespace (`…datastore`, `…room`, `…parseroom`) from `:synchronization-core` so
generated `R`/`BuildConfig` don't collide.

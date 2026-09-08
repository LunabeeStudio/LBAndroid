# common/synchronization/

Published artifacts under `studio.lunabee.synchronization`. Root `AGENTS.MD` rules (detekt, commits,
changelog, KDoc, named-args, versioning) apply here — this file only adds what's specific to these
modules.

| Module | Path | Type | Namespace | Version const | Has README |
|---|---|---|---|---|---|
| `:synchronization-core` | `synchronization-core/` | KMP (`commonMain`, JVM + iOS targets, **no Android target**) | — | `SYNCHRONIZATION_CORE_VERSION` | yes — flow/sequence diagrams; sharp edges documented below |
| `:synchronization-events` | `synchronization-events/` | KMP android-library (`commonMain`+`androidMain`+`iosMain`) | `studio.lunabee.synchronization.events` | `SYNCHRONIZATION_EVENTS_VERSION` | no — documented below |
| `:synchronization-core-datastore` | `synchronization-core-datastore/` | KMP (`commonMain`+`androidMain`+`iosMain`) | `studio.lunabee.synchronization.datastore` | `SYNCHRONIZATION_CORE_DATASTORE_VERSION` | no — documented below |
| `:synchronization-core-room` | `synchronization-core-room/` | KMP + Room/KSP (`commonMain`+`androidMain`+`iosMain`) | `studio.lunabee.synchronization.room` | `SYNCHRONIZATION_CORE_ROOM_VERSION` | no — documented below |
| `:synchronization-parse-room` | `synchronization-parse-room/` | KMP android-library (`commonMain`+`androidMain`) | `studio.lunabee.synchronization.parseroom` | `SYNCHRONIZATION_PARSE_ROOM_VERSION` | **yes — read it first** |

The engine (`:synchronization-core`) is **storage-agnostic**: it persists sync cursors through the
`SyncTimestampLocalDataSource` interface and never constructs a backend. A backend module provides the concrete
store; the app installs it once via `LBSyncStorage.install(...)` (see "Cursor storage" below).
`:synchronization-core-datastore` is the DataStore backend (preserves the legacy on-disk cursor file);
`:synchronization-core-room` is the Room backend (standalone DB, monitoring-room pattern);
`:synchronization-events` ships the platform event listeners (Android/iOS foreground + connectivity)
consumed via `LBSyncOperator.registerEventListeners(...)`. Type-safe
accessors: `projects.synchronizationCore`, `projects.synchronizationEvents`,
`projects.synchronizationCoreDatastore`, `projects.synchronizationCoreRoom`,
`projects.synchronizationParseRoom`.

`:synchronization-parse-room` is a Parse↔Room implementation layered on `:synchronization-core`
(storage-agnostic — its managers use the no-store `LBSyncManager(logging)` constructor, so the consumer
picks the backend). Its `README.md` is the source of truth for that module (source-set split, the
BaseDao `@Upsert` trick, why no KSP lives there, the `api`-vs-`implementation` leakage rules). Don't
duplicate it here — read it before touching that module.

Both modules were **moved from `LunabeeStudio/Libraries_Android`** (commits 17d6452, d165c26), so the
code predates this repo's conventions and version lineage (the migration shim mentions "3.8.0" though
the artifact is at 2.0.0 here).

## The `:synchronization-core` engine

Generic sync framework, entirely in `commonMain` (JVM + iOS targets, no Android): `LBSyncManager`,
`LBSyncGroup`, `LBSyncOperator`, the pure `runner/SyncRunner`, the framework-agnostic
`store/SyncTimestampLocalDataSource` **interface** (`SyncKey` keys, `kotlin.time.Instant` dates; the
DataStore backend persists epoch-millis under the key scheme `"${syncKey}lastSyncDate"` /
`…_localDate`), and the `store/LBSyncStorage` registry. No backend (DataStore/Room) is referenced from
core. The platform integrations (connectivity, app lifecycle) live in `:synchronization-events` as
`LBSyncEventListener` implementations.

Async primitive is **Kotlin coroutines/Flow** — no Bolts `Task`, no `GlobalScope`, no completion
callbacks (those were purged in the `feature/lbsync` 2.0.0 rewrite; Bolts now only exists transitively
inside the Parse SDK). Each level has ONE suspend entry point returning `LBResult<Unit>`:
`LBSyncManager.synchronize()`, `LBSyncGroup.syncManagers()`, `LBSyncOperator.syncAllManagers()` — but the
first two are **`internal`**: every public sync request goes through `LBSyncOperator` (see "Single sync
entry point" below).
Detached-from-caller execution (receiver-triggered syncs, automatic retry) runs in an injected,
library-owned `CoroutineScope` — the no-store constructor defaults it to the shared internal
`defaultSyncScope` (`CoroutineScope(SupervisorJob() + Dispatchers.IO)`). The single-flight
collapse-and-join + failure-retry machinery is extracted into the `SyncRunner` deep module in
`commonMain` (`runner/SyncRunner.kt`), unit-tested in isolation with virtual time. The Android-target
modules (`:synchronization-events`, both backends, `:synchronization-parse-room`) declare `minSdk 24`
(`AndroidConfig.SynchronizationMinSdk`) — the connectivity listener relies on
`registerDefaultNetworkCallback` (API 24) — while the rest of the repo stays at `minSdk 23`.
`:synchronization-core` itself has no Android target.

### Single sync entry point

Client-facing 2.0.0 → 2.1.0 migration: `MIGRATION-SYNCHRONIZATION-2.1.0.MD` (agent-executable, same shape as
`compose/presenter/MIGRATION_V2.MD`).

`LBSyncOperator` is the only public way to start a sync: `syncAllManagers()`, `sync(group)`,
`syncGroup(name)`, `sync(manager)`, `sync<T>()` (reified lookup over the registry, same match as
`syncManager<T>()`). A `syncGroup`/`sync<T>()` lookup miss → `Failure(IllegalArgumentException)`.
`LBSyncManager.synchronize()` and `LBSyncGroup.syncManagers()` are `internal` (still callable from
`commonTest`, which is a friend source set — the existing tests call them directly).

Requests are serialized by a private `Mutex` in the operator, held for the whole run: a `sync(manager)`
queues behind an in-flight `syncAllManagers()` instead of racing it, so the "put the dependency in an
earlier group" rule also holds for direct requests. The lock is NOT held while starting/stopping the
server-notification listeners (`handleEventData`), and `triggerRefresh` takes it around its launched
group loop.

Two paths deliberately escape the lock:
- **automatic retry** — `SyncRunner` re-runs the pipeline block directly, detached. It cannot take the
  operator lock: the operator awaits managers while holding it, and a retry blocked on that lock would
  deadlock against the collapsed follow-up run. `retryTempo = null` disables retry per manager.
  `SyncRunner`'s "a new explicit request pre-empts a pending retry" only holds from `run()` entry, and the
  operator lock delays that — so each operator entry point calls `cancelPendingRetry()` on the managers it
  targets **before** taking the lock (`SyncRunner.cancelPendingRetry()`, `LBSyncGroup.cancelPendingRetries()`).
  Without it, a retry parked at +`retryTempo` fires in front of a request already queued, then that request
  collapses onto a follow-up behind it. A retry scheduled *after* the enqueue (a run failing while the
  request waits) is still pre-empted by `run()` itself.
- **re-entrancy** — the `Mutex` is not reentrant, so calling an operator sync API from inside a manager's
  SPI (`fetchRequest`, `pushObjectsToServer`, …) deadlocks. Fire-and-forget from a listener callback is
  fine (`LBParseRoomSyncManager`'s LiveQuery hook does `liveQueryScope.launch { LBSyncOperator.sync(…) }`).

### Cursor storage (pluggable backend)

`SyncTimestampLocalDataSource` (commonMain interface) is the whole storage contract: `suspend`
`lastServerSyncDate` / `lastSuccessfulSyncDate` / `saveSyncDates` / `clear` / `clearAll`. Keys are the
`SyncKey` value class (`LBSyncManager.syncKey: SyncKey`), dates are `kotlin.time.Instant`, non-null-write
semantics (a `null` argument leaves that cursor unchanged). `statusByKey()` on group/operator is keyed by
`SyncKey`. **Only cursors are persisted — status is derived**, not stored.

Wiring is a **one-line install** at startup — there is no automatic classpath wiring (the KMP Android
library format merges no component manifest, and iOS has no classpath init, so App-Startup-style
self-registration is impossible here):

```kotlin
// Android, DataStore backend (preserves the legacy cursor file com.lunabee.lbsynchronization)
LBSyncStorage.install(context.dataStoreSyncTimestampLocalDataSource())
// Android, Room backend
LBSyncStorage.install(context.roomSyncTimestampLocalDataSource())
// iOS: dataStoreSyncTimestampLocalDataSource() / roomSyncTimestampLocalDataSource() (no Context)
```

`LBSyncManager`'s no-store constructor (`LBSyncManager(logging)`) reads `LBSyncStorage.requireStore()`
**lazily** (on first cursor access), so install only has to run before the first sync, not before
managers are created. `requireStore()` throws with a "add a backend / call install" message if none was
installed. The `internal` primary constructor `(providedTimestampStore, scope, logging)` injects a store
directly for tests/DI. There is **no `Context` constructor anymore** (removed), and
`LBSyncOperator.resetAllTimestamps()` takes **no `Context`** (uses the installed store).

Backends: `:synchronization-core-datastore` (`DataStoreSyncTimestampLocalDataSource` over a process-wide
`preferencesDataStore` delegate, file base name `SyncDataStoreName` = `com.lunabee.lbsynchronization`
so existing installs keep their cursors); `:synchronization-core-room` (standalone
`@Database`/`@Entity`/`@Dao`, `saveSyncDates` = `INSERT OR IGNORE` + `UPDATE … COALESCE` so a null arg
preserves the stored cursor). The Room backend starts with a fresh table, so switching a
DataStore-based app to Room **re-syncs once**. Every factory returns a process-wide single instance
(safe to call repeatedly; later calls ignore the parameters). The Room factories default to
`BundledSQLiteDriver` (own SQLite, version-stable) but accept any `SQLiteDriver` —
`roomSyncTimestampLocalDataSource(driver = AndroidSQLiteDriver())` to use the platform SQLite instead — and
leave Room's default query context unless a `dispatcher` is passed.

Because the read is I/O, the **read/reset API is `suspend`**: `lastSuccessfulSyncDate()`,
`resetTimeStamp()`, `LBSyncOperator.resetAllTimestamps()`. Status is **not seeded in the constructor** —
call `suspend LBSyncManager.load()` (or `LBSyncOperator.loadAllStatuses()` for every managed manager)
once at startup; until then status is `NeverSync`.

The old SharedPreferences default-prefs migration is gone, so an app upgrading from the
`Libraries_Android` `lb-synchronization` **re-syncs once** (the old cursor file is not read).

Three layers, top to bottom:

- **`LBSyncOperator`** (object/singleton) — app-wide registry. Holds `groups: LinkedHashMap<String,
  LBSyncGroup>`. `registerEventListeners(listeners)` wires `LBSyncEventListener`s (each `register`
  returns a `Job`; re-registering cancels the previous set): listeners emit `LBSyncRefreshEventData`
  and the operator triggers the matching refresh (`InternetIsBack`, `AppForeground`) and starts/stops
  the server-notification listeners (e.g. Parse LiveQuery) on foreground/background transitions. The
  platform listeners — `LBNetworkEventListener` (Android needs a `Context`, iOS is an object) and
  `LBAppForegroundEventListener` — ship in `:synchronization-events`. There is no broadcast bridge
  anymore — `LBSyncApplication` was removed. `syncManager<T>()` finds a registered manager by type.
- **`LBSyncGroup`** — managers in the **same group sync in parallel** (`async`/`awaitAll` over their
  `LBResult`s; a failing sibling never cancels the others — `whenAll` parity); the **operator runs
  groups sequentially**. `syncManagers()` is `internal` — sync a group with `LBSyncOperator.sync(group)`. So model table dependencies by putting the dependency in an earlier group. A
  single `var isEnabled: suspend () -> Boolean` gates a whole group (e.g. only when logged in),
  evaluated once per attempt — a blocked group sets its managers to `Disabled` and fails with
  `LBSyncClosureException`. `refreshEvents` carry a per-event min-delay debounce (`Duration`).
- **`LBSyncManager<ServerData, LocalData, PageInfo>`** — abstract per-entity engine. Pipeline is
  download → upload (then re-download unless `supportChangeNotificationFromServer()`). The subclass SPI
  is **suspend + throw-based**: `fetchRequest(...)` returns a `FetchPage`, `pushObjectsToServer(...)`,
  and `start`/`stopServerNotificationListener(): Boolean`; errors are thrown and the engine maps them to
  the `*WithError` statuses at the pipeline boundary. Override the `open` hooks for paging
  (`queryPageSize`/`hasNextPage`), incremental sync (`supportIncrementalSync`), and server push
  notifications. The `internal` primary constructor injects a `SyncTimestampLocalDataSource` + `CoroutineScope`
  (used by JVM host tests with fakes + a `TestScope`); the public no-store `LBSyncManager(logging)`
  constructor resolves the installed backend lazily via `LBSyncStorage` (there is no `Context`
  constructor). Typealiases: `LBGenericSyncManager = <*,*,*>`, `LBDefaultSyncManager<S,L> = <S,L,Nothing>`.

Status & observation: `LBSyncProcessStatus` (sealed, immutable, `kotlin.time.Instant`-based) is exposed
as `LBSyncManager.status: StateFlow<LBSyncProcessStatus>` (collect it; `currentSyncStatus` is a
read-only alias for `status.value`). `LBSyncGroup`/`LBSyncOperator` add a combined
`statusByKey(): Flow<Map<String, LBSyncProcessStatus>>` and `isSyncing(): Flow<Boolean>` (snapshot of
the registry at collection time; KDoc spells out the snapshot + `syncKey`-collision caveats). Multiple
failures aggregate into `LBSyncAggregateException`. App foreground/background is observed by
`:synchronization-events`' `LBAppForegroundEventListener` (no custom `Application` needed).

### Sharp edges

- **`isProcessing()` returns `true` for `UploadFinishSuccessfully` / `DownloadFinishSuccessfully`** —
  they're mid-pipeline steps, not terminal. Only `Sync*`/`NeverSync`/`Disabled`/`Cancelled`/`*WithError`
  are done.
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
  surfaces the terminal status `Cancelled` (so `isProcessing()` / `isSyncing` drop to `false`).
- Concurrent `synchronize()` calls **collapse into a single follow-up run** whose real `LBResult` every
  caller receives — the old immediate-success-while-dirty behavior is gone. Above that, the operator lock
  serializes the requests themselves, so the collapse now only kicks in for the paths that bypass the
  operator (automatic retry, and a manager reached from two operator requests that were already queued).
- **Never call `LBSyncOperator.sync*` from inside a manager's SPI** — the operator's `Mutex` is not
  reentrant, and the calling coroutine already holds it. Deadlock, not an exception.
- `StateFlow` is conflated (status is state, not an event stream) and observer threading is the
  collector's choice — the old synchronous-background-thread closure sharp edge no longer applies.

## Changelog

`synchronization-core/CHANGELOG.MD` is **frozen legacy** (header literally says "Deprecated, please
update the main Changelog"). Per root `AGENTS.MD`, user-visible changes go in the **root**
`CHANGELOG.MD`; bump the touched module's `*_VERSION` in `buildSrc/.../AndroidConfig.kt`. Reference
modules with type-safe accessors: `projects.synchronizationCore`, `projects.synchronizationEvents`,
`projects.synchronizationCoreDatastore`, `projects.synchronizationCoreRoom`,
`projects.synchronizationParseRoom`.

## Build & verify

Standard repo flow (see root `AGENTS.MD`). Quick reference:

```bash
./gradlew :synchronization-core:assemble :synchronization-events:assemble \
  :synchronization-core-datastore:assemble :synchronization-core-room:assemble \
  :synchronization-parse-room:assemble
./gradlew :synchronization-core:jvmTest                       # engine tests (commonTest) on the JVM target
./gradlew :synchronization-core-datastore:testAndroidHostTest # DataStore round-trip tests on the JVM host
./gradlew detekt -Pstudio.lunabee.detekt.skipDependencySorting   # drop the flag if *.gradle*/*.toml changed
```

Engine unit tests (`SyncRunner`, manager pipeline, group, operator, combined flows) live in
`commonTest`, run on the JVM target (`jvmTest`) with `runTest` + virtual time and an in-memory
`SyncTimestampLocalDataSource` fake — no device needed. The DataStore backend has its own round-trip tests; the Room backend has none (no
context-free host DB builder, matching `monitoring-room`) — it shares the `SyncTimestampLocalDataSource` contract.

The events, backend and parse-room modules opt into `kotlin.time.ExperimentalTime` where needed and
each keep a distinct android namespace (`…events`, `…datastore`, `…room`, `…parseroom`) so generated
`R`/`BuildConfig` don't collide.

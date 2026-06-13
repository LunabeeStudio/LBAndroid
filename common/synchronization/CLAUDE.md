# common/synchronization/

Two published artifacts under `studio.lunabee.synchronization`. Root `AGENTS.MD` rules (detekt,
commits, changelog, KDoc, named-args, versioning) apply here — this file only adds what's specific to
these modules.

| Module | Path | Type | Namespace | Version const | Has README |
|---|---|---|---|---|---|
| `:synchronization` | `synchronization/` | KMP android-library (`commonMain`+`androidMain`) | `studio.lunabee.synchronization` | `SYNCHRONIZATION_VERSION` | no — documented below |
| `:synchronization-parse-room` | `synchronization-parse-room/` | KMP android-library (`commonMain`+`androidMain`) | `studio.lunabee.synchronization.parseroom` | `SYNCHRONIZATION_PARSE_ROOM_VERSION` | **yes — read it first** |

`:synchronization-parse-room` is a Parse↔Room implementation layered on `:synchronization`. Its
`README.md` is the source of truth for that module (source-set split, the BaseDao `@Upsert` trick, why
no KSP lives there, the `api`-vs-`implementation` leakage rules). Don't duplicate it here — read it
before touching that module.

Both modules were **moved from `LunabeeStudio/Libraries_Android`** (commits 17d6452, d165c26), so the
code predates this repo's conventions and version lineage (the migration shim mentions "3.8.0" though
the artifact is at 2.0.0 here).

## The `:synchronization` engine

Generic sync framework. **Source-set split**: the whole engine (`LBSyncManager`, `LBSyncGroup`,
`LBSyncOperator`, connectivity, lifecycle) lives in `androidMain`; the only `commonMain` code is the
sync-cursor persistence — `store/SyncTimestampStore`, a DataStore-backed deep module that speaks epoch
millis and owns the legacy key scheme (`"${syncKey}lastSyncDate"` / `…_localDate`).

Async primitive is **Kotlin coroutines/Flow** — no Bolts `Task`, no `GlobalScope`, no completion
callbacks (those were purged in the `feature/lbsync` 2.0.0 rewrite; Bolts now only exists transitively
inside the Parse SDK). Each level has ONE suspend entry point returning `LBResult<Unit>`:
`LBSyncManager.synchronize()`, `LBSyncGroup.syncManagers()`, `LBSyncOperator.syncAllManagers()`.
Detached-from-caller execution (receiver-triggered syncs, automatic retry) is preserved by an injected,
library-owned `CoroutineScope` — the `Context`-based manager constructor defaults it to the shared
internal `defaultSyncScope` (`CoroutineScope(SupervisorJob() + Dispatchers.IO)`). The single-flight
collapse-and-join + failure-retry machinery is extracted into the `SyncRunner` deep module in
`commonMain` (`runner/SyncRunner.kt`), unit-tested in isolation with virtual time.

### Timestamp persistence (DataStore)

Cursors are persisted in AndroidX DataStore Preferences (no more SharedPreferences). `androidMain`'s
`store/SyncTimestampStoreProvider.kt` declares a **top-level `preferencesDataStore` delegate** with file
base name `com.lunabee.lbsynchronization` — the delegate enforces **one `DataStore` instance per
process** (constructing a second store for the same file throws), so both `LBSyncManager` and
`LBSyncOperator` obtain the *same* `SyncTimestampStore` via the internal `Context.syncTimestampStore`
getter (built from `applicationContext`). Never instantiate the delegate/store a second time.

Because the read is now I/O, the **timestamp read/reset API is `suspend`**: `lastSuccessfulSyncDate()`,
`resetTimeStamp()`, `LBSyncOperator.resetAllTimestamps()`, `LBSyncGroup.resetAllTimestamps()`. Status is
**no longer seeded in the constructor** — call `suspend LBSyncManager.load()` (or
`LBSyncOperator.loadAllStatuses()` for every managed manager) once at startup; until then status is
`NeverSync`.

The old SharedPreferences default-prefs migration is gone, so an app upgrading from the
`Libraries_Android` `lb-synchronization` **re-syncs once** (the old cursor file is not read).

Three layers, top to bottom:

- **`LBSyncOperator`** (object/singleton) — app-wide registry. Holds `groups: LinkedHashMap<String,
  LBSyncGroup>`. `initNetworkListener` / `initAppLifecycleListener` register `BroadcastReceiver`s that
  trigger refreshes on `InternetIsBack` / `AppForeground`, and start/stop server-notification
  listeners (e.g. Parse LiveQuery) on foreground/background. `syncManager<T>()` finds a registered
  manager by type.
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
  notifications. The primary constructor injects `SyncTimestampStore` + `CoroutineScope` (used by JVM
  host tests with fakes + a `TestScope`); the secondary `Context` constructor preserves the legacy call
  shape. Typealiases: `LBGenericSyncManager = <*,*,*>`, `LBDefaultSyncManager<S,L> = <S,L,Nothing>`.

Status & observation: `LBSyncProcessStatus` (sealed, immutable, `kotlin.time.Instant`-based) is exposed
as `LBSyncManager.status: StateFlow<LBSyncProcessStatus>` (collect it; `currentSyncStatus` is a
read-only alias for `status.value`). `LBSyncGroup`/`LBSyncOperator` add a combined
`statusByKey(): Flow<Map<String, LBSyncProcessStatus>>` and `isSyncing(): Flow<Boolean>` (snapshot of
the registry at collection time; KDoc spells out the snapshot + `syncKey`-collision caveats). Multiple
failures aggregate into `LBSyncAggregateException`. `LBSyncApplication` (extends `LBLifecycleApplication`
from `:core-android`) is the `Application` base class that broadcasts the foreground/background intents
the operator listens for.

### Sharp edges

- **`isProcessing()` returns `true` for `UploadFinishSuccessfully` / `DownloadFinishSuccessfully`** —
  they're mid-pipeline steps, not terminal. Only `Sync*`/`NeverSync`/`Disabled`/`*WithError` are done.
- **Per-manager cursor keys default to the class simple name** via `open val syncKey`
  (`"${syncKey}lastSyncDate"`), persisted in the DataStore file `com.lunabee.lbsynchronization`.
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

`synchronization/CHANGELOG.MD` is **frozen legacy** (header literally says "Deprecated, please update
the main Changelog"). Per root `AGENTS.MD`, user-visible changes go in the **root** `CHANGELOG.MD`;
bump the touched module's `*_VERSION` in `buildSrc/.../AndroidConfig.kt`. Reference modules with
type-safe accessors: `projects.synchronization`, `projects.synchronizationParseRoom`.

## Build & verify

Standard repo flow (see root `AGENTS.MD`). Quick reference:

```bash
./gradlew :synchronization:assemble :synchronization-parse-room:assemble
./gradlew :synchronization:testAndroidHostTest   # runs the commonTest + androidHostTest engine tests on the JVM host
./gradlew detekt -Pstudio.lunabee.detekt.skipDependencySorting   # drop the flag if *.gradle*/*.toml changed
```

Engine unit tests (`SyncRunner`, manager pipeline, group, operator, combined flows) live in
`commonTest` (the pure `SyncRunner`) and `androidHostTest` (the `androidMain` engine), run via the
`withHostTest {}` setup with `runTest` + virtual time and fakes — no device needed.

`:synchronization-parse-room` opts into `kotlin.time.ExperimentalTime` in all source sets, and keeps a
distinct android namespace (`…parseroom`) from `:synchronization` so generated `R`/`BuildConfig` don't
collide.

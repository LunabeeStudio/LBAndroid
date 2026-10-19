# Synchronization

Coroutine-native, storage-agnostic remote↔local sync engine (`studio.lunabee.synchronization:*`). Pluggable cursor stores, platform event triggers, ready-made remote/Parse managers, lint guard.

> Client migration: `common/synchronization/MIGRATION-SYNCHRONIZATION-2.1.0.MD`. Agent notes: `common/synchronization/CLAUDE.md` (source of truth for sharp edges).

## What it solves

- Keep local stores synced with backend per entity: paged incremental download, upload pending local changes, re-download.
- Order syncs across entities (dependencies in earlier groups), serialize every request app-wide.
- Re-trigger syncs on reconnection / app foreground, retry failures, expose observable status for UI.
- Persist only **cursors** (last server `updatedAt` + last success date). Status derived, never stored.

## Module map

All under `common/synchronization/`. Every module's `*_VERSION` aliases `SYNCHRONIZATION_CORE_VERSION` in `buildSrc/src/main/kotlin/AndroidConfig.kt`. Published modules pinned by `common/bom`. Convention plugins: see [[build-and-publishing]].

| Module (path) | Platform | Role |
|---|---|---|
| `synchronization-core` | KMP: JVM + iOS, **no Android** | Engine: operator, groups, managers, `SyncRunner`, `RequestCoalescer`, cursor-store interface |
| `synchronization-events` | KMP: Android + iOS | `LBNetworkEventListener`, `LBAppForegroundEventListener` (platform triggers) |
| `synchronization-core-datastore` | KMP: Android + iOS + JVM | DataStore cursor backend (keeps legacy file `com.lunabee.lbsynchronization`) |
| `synchronization-core-room` | KMP: Android + iOS (Room/KSP) | Room cursor backend (standalone DB, `sync_timestamp` table) |
| `synchronization-remote` | KMP: JVM + iOS, no Android | Backend-agnostic final managers over two small data-source interfaces |
| `synchronization-core-room` (contract) | ↑ | Also holds `LBRoomSyncModel` + `LBRoomSyncDao` (`roomsyncmanager` package, moved from parse-room in 2.2.0, FQN unchanged) |
| `synchronization-parse-ktor-room` | KMP: Android + iOS | Parse↔Room on `parse-ktor` (#373), no Parse SDK: `LBParseRoomClass` → `synchronization-remote` managers. Supersedes parse-room |
| `synchronization-parse-room` | KMP: Android + iOS (managers + LiveQuery in `androidMain` only; iOS gets `commonMain` contract) | Legacy Parse SDK ↔ Room managers + LiveQuery client. Superseded, kept until consumers moved; `api(core-room)` for contract |
| `checks` (`:synchronization-checks`) | JVM (Android Lint) | `SyncOperatorReentrantCall` lint rule; no publish conventions, ships via `lintPublish` |

Android-target modules use `minSdk 24` (`AndroidConfig.SynchronizationMinSdk`, for `registerDefaultNetworkCallback`). Rest of repo is 23.

## Core concepts and invariants

- **`LBSyncOperator`** (singleton): registry of named `LBSyncGroup`s, **only public way to start sync** (`syncAllManagers`, `sync(group)`, `syncGroup(name)`, `sync(manager)`, `sync<T>()`). `LBSyncManager.synchronize()` / `LBSyncGroup.syncManagers()` are `internal`. Lookup miss → `Failure(IllegalArgumentException)`.
- **`LBSyncGroup`**: managers run parallel (`async`/`awaitAll`, failing sibling never cancels others) or one by one with `LBSyncExecutionMode.Sequential`. Groups run sequentially in registration order. Failed group never short-circuits later ones. `isEnabled: suspend () -> Boolean` gates group (closed → managers `Disabled`, `LBSyncClosureException`).
- **`LBSyncManager<ServerData, LocalData, PageInfo>`**: per-entity pipeline download → upload → re-download (skipped if `supportChangeNotificationFromServer()`). `uploadBeforeDownload()` flips order. Suspend + throw SPI (`fetchRequest` → `FetchPage`, `pushObjectsToServer`). Throws map to `*WithError` statuses.
- **`SyncRunner`**: per-manager single-flight. Callers during run collapse into **one** follow-up run; all get its real result. Failed run with nothing queued retries after `retryTempo` (default 30 s, `null` disables, result discarded).
- **`syncOrJoin(group|manager|<T>)`**: joins `syncOrJoin` request for same target **still waiting for lock**. Never joins started run (so change made before call always read). Burst costs max one run behind current. Cancelled owner hands request to joiner. Backed by `runner/RequestCoalescer`.
- **`withSyncLock(block)`**: runs non-sync work (e.g. logout clear) under operator lock. Syncs requested inside refused; nested `withSyncLock` throws `LBSyncReentrantCallException`.
- **Status**: `status: StateFlow<LBSyncProcessStatus>` per manager. Group/operator expose `statusByKey()`, `isSyncing()`, `isActive()` (= syncing + `PendingSync`, i.e. queued requests), optionally filtered by group names.
- **Cursor storage**: `SyncTimestampLocalDataSource` interface, installed once via `LBSyncStorage.install(...)`, read lazily on first cursor access (`requireStore()` throws if missing). `null` write args leave cursor unchanged.
- **Events**: `LBSyncEventListener`s registered via `registerEventListeners` emit `InternetIsBack` / `AppForeground`. Operator refreshes matching groups (per-event min-delay debounce from persisted `lastSuccessfulSyncDate()`), starts/stops server-notification listeners on foreground/background.

## Data / control flow

1. Startup: `LBSyncStorage.install(backend)` → register groups → `registerEventListeners(...)` → `loadAllStatuses()` (else `NeverSync`).
2. Request (`sync*` / event / LiveQuery): re-entrancy check → `cancelPendingRetry()` on targets → mark targets `PendingSync` → take operator `Mutex` → run groups/managers.
3. Manager: `SyncRunner.run { runPipeline() }` launched in manager's detached scope (default `CoroutineScope(SupervisorJob() + Dispatchers.IO)`).
4. Download reads cursor → pages `fetchRequest` → `updateData` → `saveSyncDates(max updatedAt, now)`. Upload `objectToBeUploaded` → push.
5. Results aggregate: one failure as-is, several in `LBSyncAggregateException`.

LiveQuery path: `LBParseRoomSyncManager.onLiveQueryCreateOrUpdate` does `liveQueryScope.launch { LBSyncOperator.syncOrJoin(manager = this) }`. Coalesces upload-echo bursts that previously ran one full sync per event.

## Concurrency rules

- One private operator `Mutex`, held whole run. `sync(manager)` queues behind in-flight `syncAllManagers()`, so group ordering holds for direct requests too.
- `Mutex` **not reentrant**. Everything under it (pipelines, `isEnabled` gates, `withSyncLock` blocks) carries `SyncEngineMarker` context element. Lock-taking entry point seeing it returns `Failure(LBSyncReentrantCallException)` instead of deadlock. Request launched on unrelated scope not marked → allowed.
- Cancelled caller holding lock waits `NonCancellable` for targeted runs to end (`awaitRunEnd()` → `SyncRunner.awaitIdle()`) before rethrow. So `withSyncLock` block never overlaps still-writing detached pipeline.
- **Automatic retry bypasses lock** (would deadlock against collapsed follow-up). So entry points cancel pending retries *before* locking. Set `retryTempo = null` where retry must not overlap `withSyncLock`.
- Cancelled `PendingSync` request restores previous status unless pipeline already moved it.

## Non-obvious business rules

- `syncKey` (default: class simple name) **is persisted key**. Renaming manager silently resets cursor unless `syncKey` overridden. Remote managers take `syncKey` in constructor so instances don't share cursor.
- Incremental sync needs `fetchRequest` results in **ascending `updatedAt`**. Cursor keeps max seen. `FetchPage.maxUpdatedAt` lets filtered-out records still move cursor.
- Remote pull must page by **keyset cursor, never offset** (update mid-download shifts rows, skips one). `LBRemotePullSyncManager` buffers all pages, writes once, moves cursor only after write. Hence `supportIncrementalSync() = false`.
- `LBRemoteSyncManager` uploads one object at a time, stops at first failure. `markPushed` conditional so edits during upload stay pending.
- `isProcessing()` is `true` for `DownloadFinishSuccessfully` / `UploadFinishSuccessfully` (mid-pipeline, not terminal).
- Switching DataStore → Room backend, or upgrading from `Libraries_Android` `lb-synchronization`, **re-syncs once**.
- Parse-room keeps DAOs/`@Database` in consumer: no KSP in library, abstract `@Upsert` BaseDao pattern (see its `README.md`). Same for parse-ktor-room.

## Parse↔Room on parse-ktor

`synchronization-parse-ktor-room` (`studio.lunabee.synchronization.parsektorroom`). Consumer declares one `LBParseRoomClass<D : ParseObject, R : LBRoomSyncModel>(serializer)` per Parse class: `tableParseName`, `keysToSelect`, `keysToInclude`, `createObjectFrom`, `localIdKey`, `update(from)` (names mirror `LBParseRoomSyncManager` → mechanical port). `pullManager(syncKey, parse, dao)` → `LBRemotePullSyncManager`; `syncManager(...)` → `LBRemoteSyncManager`, needs `localIdKey` (else `IllegalArgumentException`). Usage: module `README.md`.

- **Pull**: parse-ktor serializer `pull` (keyset `updatedAt|objectId`, `updatedAt > cursor`, keys/include). Local side skips pulled rows whose `lbLocalId` is in `notInSync()` → pull never overwrites unpushed edit.
- **Push**: `findServerId` = `lbServerId`, else `GET classes/<C>` `where {localIdKey: lbLocalId}` `limit 1` `keys=objectId` → `PUT` found / `POST` none. Lookup prevents duplicate on double push or lost create response. Body = `update(row)` encoded with `encodeDefaults = true` (default-valued fields still sent), nulls kept, `objectId`/`createdAt`/`updatedAt` stripped.
- **Soft delete only**: `lbDeleted` pushed through `update`, tombstones pulled back. Never HTTP `DELETE`.
- **`keysToSelect` restricted** → must include `localIdKey` + soft-delete field, else pulled row maps to `objectId` local id (duplicate) and tombstones lost.
- **Known gap**: `markPushed` unconditional (`markInSync(listOf(lbLocalId))`) → edit made during upload marked in sync, waits for next edit.
- **Carry-over**: pass old parse-room manager class simple name as `syncKey` → same cursor meaning (max `updatedAt`), no full resync at switch.
- Out of scope: LiveQuery, hard delete, `ParseUser` sync (app-side).

## `checks` module

`SyncOperatorReentrantCallDetector` flags call to `sync`, `syncAllManagers`, `syncGroup`, `syncOrJoin` or `withSyncLock` inside member annotated `@SyncEngineCallback`. Annotation marks exactly SPI pipeline calls under lock. Shipped to consumers via `lintPublish(projects.synchronizationChecks)` in **both cursor backends** (core has no Android target → no `lintPublish`). Blind spots (`launch`/`async`, helper indirection, `isEnabled` lambda) covered by runtime `SyncEngineMarker` check.

## Testing

| Where | What | Run |
|---|---|---|
| `synchronization-core/src/commonTest` | `SyncRunnerTest`, `RequestCoalescerTest`, pipeline (`DownloadTest`, `UploadTest`, `ConflictTest`, `SynchronizationRetryTest`, `RefreshEventTest`), group/operator, combined flows; fixture `testfixture/SyncTestFixture.kt` | `:synchronization-core:jvmTest` |
| `synchronization-remote/src/commonTest` | Managers driven via `LBSyncOperator` with in-memory `Fake*` remote/local/cursor stores | `:synchronization-remote:jvmTest` |
| `synchronization-core-datastore/src/commonTest` | DataStore round-trip | `:synchronization-core-datastore:jvmTest` / `:testAndroidHostTest` |
| `synchronization-parse-ktor-room/src/commonTest` | Pull + push over `FakeParseServer` ([[parse]]), in-memory fake `LBRoomSyncDao`, fake cursor store | `:synchronization-parse-ktor-room:testAndroidHostTest` / `:iosSimulatorArm64Test` |
| `checks/src/test` | Lint detector with source stubs (`libs.lintTests`) | `:synchronization-checks:test` |

`runTest` + virtual time. Internal constructors inject fake `SyncTimestampLocalDataSource` and `TestScope`. `commonTest` is friend source set → calls `internal` entry points directly. Room backend and parse-room have no tests. Demo: `demo-compose/.../demo/synchronization/` (compares `sync` vs `syncOrJoin` bursts).

## Key types

Paths relative to `common/synchronization/`; `…` = `src/<sourceSet>/kotlin/studio/lunabee/synchronization`.

| Type | File |
|---|---|
| `LBSyncOperator` | `synchronization-core/…/LBSyncOperator.kt` |
| `LBSyncGroup`, `LBSyncExecutionMode` | `synchronization-core/…/LBSyncGroup.kt`, `LBSyncExecutionMode.kt` |
| `LBSyncManager`, `FetchPage`, `LBSyncProcessStatus` | `synchronization-core/…/syncmanager/` |
| `SyncRunner`, `RequestCoalescer` | `synchronization-core/…/runner/` |
| `SyncEngineMarker`, `SyncEngineCallback`, `LBSyncReentrantCallException` | `synchronization-core/…/` |
| `SyncTimestampLocalDataSource`, `LBSyncStorage`, `SyncKey` | `synchronization-core/…/store/` |
| `LBSyncEventListener`, `LBSyncRefreshEvent(Data)` | `synchronization-core/…/LBSyncEventListener.kt`, `syncmanager/` |
| `LBNetworkEventListener`, `LBAppForegroundEventListener` | `synchronization-events/…/` (common + android/ios actuals) |
| `DataStoreSyncTimestampLocalDataSource` | `synchronization-core-datastore/…/datastore/` |
| `RoomSyncTimestampLocalDataSource`, `SyncRoomDatabase` | `synchronization-core-room/…/room/` |
| `LBRemotePullSyncManager`, `LBRemoteSyncManager`, `RemotePullBuffer`, `LBRemotePage` | `synchronization-remote/…/remote/` |
| `LBRoomSyncModel`, `LBRoomSyncDao` | `synchronization-core-room/…/roomsyncmanager/` (commonMain) |
| `LBParseRoomClass`, `LBParseRemoteDataSource`, `LBRoomSyncLocalDataSource` | `synchronization-parse-ktor-room/…/parsektorroom/{,remote,local}/` |
| `LBParseRoomSyncManager`, `LBParseLiveQueryManager`, `LBRoomSyncManager` | `synchronization-parse-room/…/{parseroomsyncmanager,roomsyncmanager}/` |
| `SyncOperatorReentrantCallDetector`, `SynchronizationIssueRegistry` | `checks/…/checks/` |

Related: [[architecture]], [[parse]], [[common-libraries]], [[build-and-publishing]], [[presenter]], [[compose-ui]].
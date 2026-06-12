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

Async primitive is **Bolts `Task`** (`parse-bolts-tasks`) plus `GlobalScope.launch` — *not*
coroutines/Flow. Syncs run detached from any caller scope by design. Don't "modernize" this casually;
the public surface (`Task<Boolean>`, completion callbacks) is what consumers and the parse-room layer
extend.

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
- **`LBSyncGroup`** — managers in the **same group sync in parallel** (`Task.whenAll`); **groups run
  sequentially** (chained via `continueWithTask`). So model table dependencies by putting the
  dependency in an earlier group. `isEnableClosure` / `isEnableClosureAsync` gate a whole group (e.g.
  only when logged in) — a blocked group sets its managers to `Disabled` and fails with
  `LBSyncClosureException`. `refreshEvents` carry a per-event min-delay debounce.
- **`LBSyncManager<ServerData, LocalData, PageInfo>`** — abstract per-entity engine. Pipeline is
  download → upload (then re-download unless `supportChangeNotificationFromServer()`). Subclass and
  implement the abstract members; override the `open` ones for paging
  (`queryPageSize`/`hasNextPage`), incremental sync (`supportIncrementalSync`), and server push
  notifications. Typealiases: `LBGenericSyncManager = <*,*,*>`, `LBDefaultSyncManager<S,L> =
  <S,L,Nothing>`.

Status & observation: `LBSyncProcessStatus` (sealed) is observed via `manager.observe(closure):
LBSyncToken`. `LBSyncApplication` (extends `LBLifecycleApplication` from `:core-android`) is the
`Application` base class that broadcasts the foreground/background intents the operator listens for.

### Sharp edges

- **`isProcessing()` returns `true` for `UploadFinishSuccessfully` / `DownloadFinishSuccessfully`** —
  they're mid-pipeline steps, not terminal. Only `Sync*`/`NeverSync`/`Disabled`/`*WithError` are done.
- **Per-manager cursor keys default to the class simple name** via `open val syncKey`
  (`"${syncKey}lastSyncDate"`), persisted in the DataStore file `com.lunabee.lbsynchronization`.
  **Renaming a `SyncManager` subclass silently resets its incremental-sync cursor** unless you pin a
  stable `syncKey` — the escape hatch is to **override `syncKey`** so the persisted key survives the
  rename. Treat `syncKey` as a persisted key.
- `currentSyncStatus` has an `internal set` — only the engine mutates it; never set it from a consumer.
- **Incremental sync requires `fetchRequest` results ordered by ascending `updatedAt`** — the cursor
  saves the max date seen, so out-of-order results lose records.
- Failure sets `syncIsDirty = true`, which schedules a retry `Timer` (default `retryTempoInMs = 30s`).
- The status-change closure fires **synchronously** on the setter (changelog 1.7.1) and may run on a
  background thread — keep observers cheap and thread-safe.

## Changelog

`synchronization/CHANGELOG.MD` is **frozen legacy** (header literally says "Deprecated, please update
the main Changelog"). Per root `AGENTS.MD`, user-visible changes go in the **root** `CHANGELOG.MD`;
bump the touched module's `*_VERSION` in `buildSrc/.../AndroidConfig.kt`. Reference modules with
type-safe accessors: `projects.synchronization`, `projects.synchronizationParseRoom`.

## Build & verify

Standard repo flow (see root `AGENTS.MD`). Quick reference:

```bash
./gradlew :synchronization:assemble :synchronization-parse-room:assemble
./gradlew :synchronization:test :synchronization-parse-room:test
./gradlew detekt -Pstudio.lunabee.detekt.skipDependencySorting   # drop the flag if *.gradle*/*.toml changed
```

`:synchronization-parse-room` opts into `kotlin.time.ExperimentalTime` in all source sets, and keeps a
distinct android namespace (`…parseroom`) from `:synchronization` so generated `R`/`BuildConfig` don't
collide.

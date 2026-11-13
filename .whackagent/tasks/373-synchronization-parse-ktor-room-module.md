---
issue: 373
phase: review
wiki: "[[synchronization]], [[architecture]], [[build-and-publishing]], [[parse]]"
note: "Depends on #372 (parse-ktor, parse-ktor-test). Consumers: RunMotion (phase 2/3 of the Shared KMP Data Layer plan), Kokoriko (push of TimeEntry, AiEntry)."
created: 2026-10-19
---

## Context / Decisions

spec: issue #373 body; RunMotion Shared KMP Data Layer plan (rev 109), https://claude.ai/code/artifact/467dc172-770a-4bf9-a237-8ab027f8a67c

We need a Parse↔Room sync that runs in KMP common code (Android + iOS) on `parse-ktor` (#372) instead of the Parse Android SDK. It succeeds `synchronization-parse-room`, whose managers are Android-only and bound to the SDK.

### Prior art survey

- No KMP Parse client exists (survey recorded in #372), so no KMP Parse sync library exists either.
- `synchronization-remote` (LBAndroid) already holds a backend-agnostic engine: keyset paging (`LBPullRemoteDataSource.fetchPage(cursor, updatedAfter)`), a pull buffer that saves once after the last page, cursor checkpointing, push-before-pull, and 16 tests. A backend only implements 4 data-source interfaces. We build on it.
- `synchronization-parse-room` `LBParseRoomSyncManager`: pages with limit/skip (records can be skipped when data changes mid-download) and runs a `getById` before every push. Not reused as an engine; its member names are reused for the declaration API.
- Kokoriko `TimeEntrySyncRemoteDatasource` / `AiEntrySyncRemoteDatasource`: production push on `synchronization-remote` with a server `externalId` column, `findServerId` = query by `externalId`, soft delete through a `deleted` field. Its id and delete scheme is adopted.

### Scope

- In scope: pull and push of Parse classes into Room through `synchronization-remote` managers.
- Pull matters for RunMotion: 33 of its 35 Parse managers are pull-only. Push matters for Kokoriko (time entries, AI entries) and for RunMotion's `WorkoutBotConversation`.
- Out of scope: LiveQuery. Neither RunMotion nor Kokoriko uses it. A Ktor WebSocket LiveQuery client would be its own ticket.
- Out of scope: hard deletes. The library never sends `DELETE`.
- `synchronization-remote` API: one narrow breaking change, decided in feedback round 1. `LBSyncRemoteDataSource.create` returns the backend id, and `LBSyncLocalDataSource.markPushed` receives it, so a created row can store its `lbServerId`. It is documented as `**break:**` in `CHANGELOG.MD`.
- Out of scope: RunMotion's custom `UserSyncManager` (`ParseUser`). It stays app-side.
- `synchronization-parse-room` stays as is, apart from the contract move below. It gets a README note "Superseded by `synchronization-parse-ktor-room`; removed in a future major once consumers moved." No `@Deprecated`: there is no mechanical replacement, and deprecation warnings would break consumers that treat warnings as errors.

### Modules and coordinates

- New module `common/synchronization/synchronization-parse-ktor-room`, published as `studio.lunabee.synchronization:synchronization-parse-ktor-room`, registered in `settings.gradle.kts` with the other synchronization modules.
- It uses `lunabee.kmp-android-library-conventions` (Android + iOS), like `synchronization-core-room`, because Room needs it.
- Dependencies: `api(parse-ktor)`, `api(synchronization-remote)`, `api(synchronization-core-room)`.
- `LBRoomSyncModel` and `LBRoomSyncDao` move from `synchronization-parse-room` `commonMain` to `synchronization-core-room` `commonMain`. They keep the same package and FQN. `synchronization-parse-room` gains `api(synchronization-core-room)`, so its consumers see no source or binary change. `LBParseRoomModel` stays in `synchronization-parse-room`.
- `SYNCHRONIZATION_PARSE_KTOR_ROOM_VERSION = SYNCHRONIZATION_CORE_VERSION`. The synchronization family bumps from 2.1.0 to 2.2.0 (new module, and parse-room's transitive dependencies move).
- The new module is added to the BOM with `addModule`, and the BOM version is bumped.
- Packages are feature-grouped (for example `remote/` for the Parse data source, `local/` for the Room data source, plus the declaration and factories). Implementation details stay `internal`.

### Declaration API

- A consumer declares each Parse class with an abstract class `LBParseRoomClass<D, R : LBRoomSyncModel>(serializer: KSerializer<D>)`. Its member names mirror `LBParseRoomSyncManager`, so RunMotion's ports stay mechanical:
  - `tableParseName(): String` (abstract): the Parse class name.
  - `keysToSelect(): List<String>` (open, default: all keys).
  - `keysToInclude(): List<String>` (open, default: none).
  - `createObjectFrom(serverObject: D): R` (abstract): maps a server object to a Room row, and must set `lbInSync = true`. For classes that push, `lbLocalId` comes from the `localIdKey` field, or from `objectId` for server-created rows.
  - `localIdKey: String?` (open, default `null`): the server column that stores the local id. Required for push.
  - `update(from: R): D` (open, default fails): the body sent on push. It must write `localIdKey` and the app's soft-delete field from `lbDeleted`.
- `D` is an app `@Serializable` DTO that uses `parse-ktor` types (`ParseDate`, `ParsePointer`). Apps never handle raw `JsonObject`.
- `LBParseRoomClass.pullManager(syncKey, parse: LBParseClient, dao: LBRoomSyncDao<R>)` returns a `LBRemotePullSyncManager<R>`.
- `LBParseRoomClass.syncManager(syncKey, parse, dao, pushBeforePull = false)` returns a `LBRemoteSyncManager<R>`. It throws `IllegalArgumentException` when `localIdKey` is `null`.
- `syncKey` is explicit. RunMotion can reuse each old manager's class simple name, so the stored cursor (max `updatedAt`, same meaning) carries over at the switch with no full resync.

### Remote side (`LBParseRemoteDataSource`, internal)

- It implements `LBSyncRemoteDataSource<R>` on `parse-ktor`, and decodes `D` and maps it through `createObjectFrom`.
- `fetchPage(cursor, updatedAfter)` uses `parse-ktor`'s keyset `pull` / `PullCursor` on `updatedAt|objectId`, ascending, with `updatedAt > updatedAfter`, the declared `keys` and `include`, and returns `LBRemotePage` with `maxUpdatedAt` and `nextCursor`.
- `findServerId(row)` returns `row.lbServerId` when set. Otherwise it sends `GET classes/<C>` with `where {<localIdKey>: lbLocalId}` and `limit 1`, and returns the found `objectId` or `null`. This lookup prevents a duplicate when a row is pushed twice before it is pulled back, and when a create succeeded but its response was lost.
- `create(row)` sends `POST classes/<C>` with `update(row)` and returns the `objectId` of the response, or `null` when the response has none. `update(serverId, row)` sends `PUT classes/<C>/<serverId>` with `update(row)`.
- Deletes are soft only: a row with `lbDeleted = true` is pushed through a normal update, and pull brings tombstones back as `lbDeleted = true`.

### Local side (`LBRoomSyncLocalDataSource`, internal)

- It implements `LBSyncLocalDataSource<R>` on the `LBRoomSyncDao` contract: `upsert`, `notInSync`, `markInSync`, `deleteAll`, plus two open members added in feedback round 1, `upsertExceptPending` and `markInSync(localId, serverId)`. Both have a default body, so RunMotion's and any existing DAOs compile as is.
- `savePulled(rows)` calls `upsertExceptPending`, which upserts the rows except those whose `lbLocalId` is in `notInSync()` in one Room transaction, so a pull never overwrites a local edit that is not pushed yet. This filter is the only behavior change from today's `LBRoomSyncManager`.
- `objectsToPush()` returns `notInSync()`, which includes soft-deleted rows.
- `markPushed(row, serverId)` calls `markInSync(row.lbLocalId, serverId)` when the backend id is known, and `markInSync(listOf(row.lbLocalId))` otherwise, unconditionally. The default `markInSync(localId, serverId)` does not store the id; a DAO overrides it with a table-literal `@Query` that sets `lbServerId`, so the next push skips the lookup. Known gap, kept on purpose and documented in KDoc and the README: an edit made while that row is uploading is marked in sync and waits for its next edit to be pushed.
- `clear()` calls `deleteAll()`.

### Proof and docs

- Tests in `commonTest`, run as Android host tests and on the iOS simulator. The remote side uses `FakeParseServer` from `parse-ktor-test`. The local side uses an in-memory fake `LBRoomSyncDao` subclass, with no real Room database, because the adapter only calls the 4 DAO members.
- No demo screen (there is no Parse server to call).
- A module `README.md` covers declaring a class, pull vs sync managers, `localIdKey`, soft delete, the `markPushed` gap and the `syncKey` carry-over from parse-room.
- The parse-room README gets the "superseded" note.
- A `CHANGELOG.MD` block for the synchronization family at 2.2.0 (new module, contract moved to `core-room`) and the BOM bump.
- KDoc on every public declaration.
- The `[[synchronization]]` wiki page is updated.

### Coding decisions

- The DTO is bounded `D : ParseObject`, because the keyset cursor of `pull` needs each object's `objectId` and `updatedAt`. (autopilot assumption)
- `parse-ktor` `query` and `pull` gain optional `keys` and `include` parameters, plus overloads that take a `KSerializer` for callers without a reified type. This is folded into the unreleased `parse-ktor` 1.0.0, with no version bump. (autopilot assumption)
- The push body is the JSON encoding of `update(row)` without the reserved `objectId`, `createdAt` and `updatedAt` fields. (autopilot assumption)
- `findServerId` asks only for `objectId` (`keys=objectId`) on top of `where` and `limit 1`. (autopilot assumption)
- The BOM stays at 4.17.0, the unreleased bump of #372 below this ticket in the stack. The synchronization 2.2.0 lines join the same `CHANGELOG.MD` release block. (autopilot assumption)
- `synchronization-core-room` exposes the Room runtime as `api`, because the public `LBRoomSyncDao` now lives there and uses `@Upsert`. (autopilot assumption)

## Acceptance criteria

1. `./gradlew :synchronization-parse-ktor-room:build :synchronization-core-room:build :synchronization-parse-room:build` succeeds, including the Android host and iOS simulator test tasks.
2. `./gradlew detekt` passes (plain run, because Gradle files change).
3. `./gradlew :synchronization-parse-ktor-room:PrintCoordinates` prints `studio.lunabee.synchronization:synchronization-parse-ktor-room:2.2.0`, and the other synchronization modules print 2.2.0.
4. `./gradlew publishList` lists the new module, and the BOM declares a constraint for it.
5. `LBRoomSyncModel` and `LBRoomSyncDao` live in `synchronization-core-room` with their original FQNs, and `synchronization-parse-room` compiles with no source change outside its build file.
6. A pull manager built with `pullManager` requests `classes/<tableParseName>` with `updatedAt` ascending, the declared `keys` and `include`, and `updatedAt > ` the stored cursor.
7. A pull spanning 3 pages follows the keyset cursor (`updatedAt` and `objectId` combined with `$or`), calls `upsert` once after the last page with the rows mapped by `createObjectFrom`, and stores the max `updatedAt` as the cursor.
8. A pulled row whose `lbLocalId` is currently in `notInSync()` is not upserted. The other rows of the same pull are. The pending check and the upsert run in one transaction (`LBRoomSyncDao.upsertExceptPending`, `@Transaction`).
9. Clearing the manager's data calls `deleteAll()`.
10. On push, a row with `lbServerId` set is sent as `PUT classes/<C>/<lbServerId>` with the body from `update(row)`, and no lookup request is made.
11. On push, a row without `lbServerId` triggers `GET classes/<C>` with `where {<localIdKey>: <lbLocalId>}` and `limit 1`. When the server returns an object, the row is sent as `PUT` to that `objectId`. When it returns none, the row is sent as `POST classes/<C>`.
12. After each successful create or update, the row is marked in sync with its `lbLocalId` and the backend id: the id found by the lookup, or the `objectId` returned by the `POST`.
13. When a push request fails, the sync run fails with the mapped `parse-ktor` error, rows already pushed stay marked in sync, and the failing row and the rows after it stay in `notInSync()`.
14. A soft-deleted row (`lbDeleted = true`) is pushed through the same create/update path, and the library never sends an HTTP `DELETE`.
15. `syncManager` on a declaration with `localIdKey == null` throws `IllegalArgumentException`.
16. The module README, the parse-room "superseded" note, KDoc on public API, the `CHANGELOG.MD` 2.2.0 block and the `[[synchronization]]` wiki update exist.
17. After a `POST` whose response holds an `objectId`, a DAO that overrides `markInSync(localId, serverId)` stores it as `lbServerId`, and the next push of that row is a `PUT` to that id with no lookup request. A `POST` response without `objectId` marks the row in sync with no server id.

## Implementation

- Brick 1, `parse-ktor`: `query` and `pull` take optional `keys` and `include`, sent comma-joined only when they are not empty. New overloads take a `KSerializer<T>`. On a decoding failure they throw `LBParseException.Decoding`, passed through the client `mapErr` (`LBParseClient.mapErr` is internal). Files: `query/ParseQuery.kt`, `client/LBParseClient.kt`, `ParseQueryTest.kt`, `common/parse/README.md`.
- Brick 2, contract move: `LBRoomSyncModel` and `LBRoomSyncDao` moved with `git mv` to `synchronization-core-room` `commonMain`, in the same package. `synchronization-core-room` exposes `androidxRoomRuntime` as `api`. `synchronization-parse-room` adds `api(projects.synchronizationCoreRoom)`. `SYNCHRONIZATION_CORE_VERSION` is now 2.2.0.
- Brick 3, new module `synchronization-parse-ktor-room`:
  - `LBParseRoomClass` provides `pullManager` and `syncManager`.
  - Internal `remote/LBParseRemoteDataSource`: serializer `pull` and the `findServerId` lookup. The push body is encoded with `Json { encodeDefaults = true }`, with the reserved fields stripped.
  - Internal `local/LBRoomSyncLocalDataSource`: skips pulled rows whose local edit is pending.
  - 13 commonTest tests on `FakeParseServer` and a fake DAO.
  - The module is registered in `settings.gradle.kts` and in the BOM with `addModule`. `SYNCHRONIZATION_PARSE_KTOR_ROOM_VERSION` is set.
- Brick 4, docs: the module `README.md`, the "superseded" note in the parse-room README, the root `README.md` row, the `CHANGELOG.MD` lines folded into the 2026-10-19 block, and the `common/synchronization/CLAUDE.md` updates.

## Review

### validation (autopilot)

- Lenses: style, elegance, structure and correctness, all four on every round. The `detekt` validation tool was clean before the first round.
- Round 1: 9 findings (1 major, 8 minor).
  - Major: the `[[synchronization]]` wiki page was not updated (AC16). The PM fixed it during the wiki sync.
  - Minor, autofixed:
    - The `keysToSelect` KDoc and README now warn that a restricted list must keep `localIdKey` and the soft-delete field.
    - The reified `query` and `pull` now delegate to the `KSerializer` overloads, so one decode and `mapErr` path remains, with one response `Json`.
    - `LBParseRoomClass.serializer` is now `internal`.
    - `localIdKey` moved first, as property-first member order.
    - Test cleanups: helper order, `FakeRoomDao.kt` split out, redundant `@Upsert` removed.
    - The contract bullets in the parse-room README were corrected.
- Round 2: 5 minor findings, autofixed or fixed by the PM.
  - The stale `@PublishedApi` was removed from internal query symbols.
  - Wiki: parse-room is back to Android + iOS (PM).
  - The `synchronization/CLAUDE.md` source-set row was corrected.
  - The test `keysToSelect` now includes `deleted`.
  - A new test covers `pushBeforePull`.
- Round 3: 1 minor finding: the push-before-pull test now asserts the exact request order (PUT, then the pull GET). Autofixed.
- Final pass: clean.

## Verification

- Library, `verify.mode: off`, so no runtime check: build and tests are the proof. No live Parse server was called.
- `./gradlew detekt` (plain run) is clean.
- `:synchronization-parse-ktor-room:build` (Android host tests) and `:synchronization-parse-ktor-room:iosSimulatorArm64Test` pass, 13 tests each (AC 1, 6–15).
- `:synchronization-core-room:build` and `:synchronization-parse-room:build` pass, with no parse-room source change outside its build file (AC 1, 5).
- `:parse-ktor:jvmTest` and `:parse-ktor:iosSimulatorArm64Test` pass, 13 `ParseQueryTest` tests each.
- `PrintCoordinates` prints 2.2.0 for all 7 synchronization modules, including `synchronization-parse-ktor-room` (AC 3).
- `publishList` lists `synchronization-parse-ktor-room`, and the BOM pom holds its constraint (AC 4).

- Round 1 (feedback): manual validation, not run by the agent. The build and tests below are the proof.
  - `:synchronization-remote:jvmTest`, `:synchronization-core-room:assemble`, `:synchronization-parse-room:assemble`, `:synchronization-parse-ktor-room:testAndroidHostTest`, `:synchronization-parse-ktor-room:iosSimulatorArm64Test` and `:parse-ktor:jvmTest` pass.
  - `detekt -Pstudio.lunabee.detekt.skipDependencySorting` is clean. No Gradle file changed.
  - A throwaway `@Dao` probe compiled `upsertExceptPending` on Android and iosSimulatorArm64, and Room generated a `performInTransactionSuspending` wrapper. The probe was then removed.

## Feedback

### Round 1 — 2026-11-13

Asked: the PR #375 review (https://github.com/LunabeeStudio/LBAndroid/pull/375#pullrequestreview-5888358260), which has six items.

1. "`savePulled` reads `dao.notInSync()` and then calls `dao.upsert(rows)` as two separate transactions [...] The local edit is lost and never pushed."
   - Triage: defect. Route: implementer.
   - Fix: `LBRoomSyncDao` gains `@Transaction open suspend fun upsertExceptPending(rows)`, and `savePulled` calls it. AC 8 now names the transaction.
2. "`ResponseJson` copies the `LBKtorJson` defaults [...] Consider exposing the client `Json` instead."
   - Triage: adjustment. Route: inline.
   - User's choice: derive the settings from `LBKtorJson.Config()`. `ResponseJson` in `ParseQuery.kt` now reads `ignoreUnknownKeys`, `explicitNulls` and `isLenient` from the default config, which is the one the client installs. The `ktor` module is unchanged.
3. "`create` drops the `objectId` returned by `POST`, so `lbServerId` stays null until a pull brings the row back."
   - Triage: adjustment, with scope widened by the user's choice ("Fix in this PR"). Route: implementer.
   - Fix, a `**break:**` in `synchronization-remote`: `LBSyncRemoteDataSource.create` returns `String?`, `LBSyncLocalDataSource.markPushed(pushed, serverId)` receives the id, and `LBRemoteSyncManager` passes it on.
   - `LBRoomSyncDao` gains an open `markInSync(localId, serverId)`. Its default keeps today's behavior, and a DAO overrides it to store `lbServerId`.
   - `LBParseRemoteDataSource.create` decodes `objectId` from the response.
   - Updated: the Context scope line, the Remote and Local side bullets, AC 12, and the new AC 17.
4. "`syncManager` checks `localIdKey` but cannot check that `update()` is overridden."
   - Triage: adjustment. Route: inline.
   - User's choice: KDoc only. `syncManager` now documents `@throws UnsupportedOperationException` at the first push when `update()` is not overridden.
5. The note that `synchronization-core-room` exposes the Room runtime as `api`.
   - Triage: informational. No change, because it was already documented as a decision and in the changelog.
6. The note on the `@PublishedApi` removal, which is binary-breaking.
   - Triage: informational. No change, because it is already documented as `**break:**`.

- Inline hunks, which were written by the main thread without a convention pass, so the verifier must judge them harder:
  - `common/parse/parse-ktor/.../query/ParseQuery.kt`: the `ResponseJson` block and the `LBKtorJson` import.
  - `LBParseRoomClass.kt`: the `@throws` line on `syncManager`.
- Inline build: `:parse-ktor:jvmTest` and `:synchronization-parse-ktor-room:compileAndroidMain` with `--rerun-tasks` are green, with no warning.
- Implementer tests: synchronization-remote has 3 new manager tests (id from create, create without id, id from lookup), and parse-ktor-room has 2 new sync tests (POST stores the id and the next push is a PUT with no lookup; a POST without `objectId`).
- Docs: the `CHANGELOG.MD` 2026-11-13 block (no version bump), the `synchronization-remote` README, the `synchronization-parse-ktor-room` README and `common/synchronization/CLAUDE.md`.
- New warnings: none.
- Review: deferred to `/wa-validate` (`review.when: on_validation`).
- Verify: manual (`verify.mode: off`).
- No rule captured.

# :synchronization-parse-room

A generic Parse↔Room synchronization framework built on top of the `:synchronization` library. KMP
`androidLibrary`, android namespace `studio.lunabee.synchronization.parseroom`. Classes live under
`studio.lunabee.synchronization.{roomsyncmanager,parseroomsyncmanager}`.

## Why this module exists

It provides a Room-backed implementation of the Parse sync framework. The sync managers extend the
`:synchronization` library (`LBSyncManager`, `LBConnectivityManager`), so Room managers are
orchestrated by the same `LBSyncOperator` groups/triggers.

## Source-set split

- **commonMain — the persistence contract** (plain Kotlin + multiplatform Room only). This lets a
  consumer keep its synced `@Entity` classes **and** the `@Database` in commonMain.
- **androidMain — the Parse-backed sync managers** (`Context` / Parse / coroutines / `:synchronization`).
  Parse queries use the Parse community **coroutines** extension artifact
  (`com.github.parse-community.Parse-SDK-Android:coroutines`); there is no Bolts dependency here anymore
  (Bolts survives only transitively inside the Parse SDK).

## Contents — `src/{commonMain,androidMain}/kotlin/studio/lunabee/synchronization/`

### commonMain (contract)

- `roomsyncmanager/LBRoomSyncModel` — interface synced Room entities implement (sync bookkeeping as
  immutable read-only properties: `lbLocalId` = primary key, `lbServerId`, `lbUpdatedAt`, `lbInSync`,
  `lbDeleted`).
- `roomsyncmanager/LBRoomSyncDao<T>` — generic persistence base. The typed `@Upsert` is resolved
  generically by Room codegen at the concrete `@Dao` subclass (BaseDao pattern). `notInSync` /
  `markInSync` / `deleteAll` are **abstract** and each concrete DAO supplies a 3-line table-literal
  `@Query` — Room can't take a table name as a runtime `@Query` param, and `@RawQuery` is read-only
  and can't map a generic `T`.
- `parseroomsyncmanager/LBParseRoomModel : LBRoomSyncModel` — marker for the Parse layer (no Parse
  import, so it stays commonMain).

### androidMain (Parse-backed managers)

- `roomsyncmanager/LBRoomSyncManager<S, R, P>` / `LBDefaultRoomSyncManager` — Room-backed
  `LBSyncManager`: persist (`upsert`), clear (`deleteAll`), upload (`notInSync` → `push` →
  `markInSync`). `createObjectFrom` must return pulled rows with `lbInSync = true`. The DAO is
  **constructor-injected** so managers are unit-testable with a fake DAO.
- `parseroomsyncmanager/LBParseRoomSyncManager<R>` — bidirectional Parse↔Room: paged suspend
  `fetchRequest` (returns a `FetchPage`), incremental `updatedAt` cursoring (`kotlin.time.Instant`),
  suspend/throw-based `push`, suspend LiveQuery listener. Abstract surface: `tableParseName()`,
  `update(parseObject, from)` (+ `createObjectFrom` from the base). `objectToBeUploaded()` returns
  full immutable entities so `push` never re-queries by primary key.
- `parseroomsyncmanager/LBParseLiveQueryManager` — singleton Parse LiveQuery client with reconnect.
  Persistence-agnostic, so this module is self-contained.

## Boundaries

- Parse / coroutine / `:synchronization` types leak into the public manager signatures (androidMain
  `api`); Room-runtime leaks into the contract (commonMain `api`). The Parse coroutines extension
  artifact is `implementation` (its extensions are called internally and never appear in a
  subclass-visible signature).
- **No KSP/Room compiler here**: only the abstract `@Upsert` base lives here (needs the Room
  annotations on the classpath). The concrete `@Dao` subclasses + `@Database` live in the consumer
  module and are processed by its own `kspAndroid`.
- Distinct android namespace (`…synchronization.parseroom`) from the `:synchronization` artifact so
  generated `R`/`BuildConfig` don't collide.

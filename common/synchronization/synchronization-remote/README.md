# :synchronization-remote

Backend-agnostic synchronization managers layered on `:synchronization-core`. KMP (`commonMain`, JVM + iOS
targets, no Android target, like the core). Classes live under `studio.lunabee.synchronization.remote`.

## Why this module exists

`:synchronization-parse-room` ties its managers to the Parse Android SDK and to Android. This module keeps only the
part every remote↔local mapping shares, so a consumer on any platform plugs its own transport (Ktor, a REST client,
a Parse REST wrapper, …) and its own store (a Room DAO, a datasource over one) behind two small interfaces. It has no
Ktor, Parse or Room dependency.

## Contents

- `LBPullRemoteDataSource<T>` — one page of objects since a cursor (`fetchPage(cursor, updatedAfter)` →
  `LBRemotePage`): strict `updatedAt > updatedAfter`, ascending `updatedAt` with a stable tie-breaker. `cursor` is
  `null` for the first page, then the previous page's `nextCursor`, and must be a keyset position (resume after the
  last record read, e.g. `(updatedAt, id) > (lastUpdatedAt, lastId)`), never an offset: a record updated during the
  download moves to the end and shifts the later ones back one slot, so an offset skips the one on the page
  boundary. The implementation maps the remote records to the local model `T`.
- `LBSyncRemoteDataSource<T>` — adds the upload of one object: `findServerId(obj)` (e.g. by external id), then
  `update(serverId, obj)` or `create(obj)`.
- `LBRemotePage<T>` — the page objects, the `nextCursor` of the following page (`null` on the last page), and a
  page-level `maxUpdatedAt` that also counts the records left out of `objects` (they still move the cursor, through
  `FetchPage.maxUpdatedAt`). `maxUpdatedAt` is the only source of the cursor, so a page holding objects must set it
  (the constructor throws otherwise).
- `LBPullLocalDataSource<T>` / `LBSyncLocalDataSource<T>` — the local store: `savePulled` (a whole download in one
  call, leaving out the objects still to upload), `clear`, and for two-way managers `objectsToPush` and
  `markPushed` (conditional: a change made during the upload stays to upload).
- `LBRemotePullSyncManager<T>` — download-only manager. Pages are buffered and saved once the last page is read, so a
  failure while paging writes nothing and the store can dedupe across pages; the cursor moves after that write, only
  when something came back. `supportIncrementalSync()` is `false`: a mid-paging checkpoint would save a cursor
  before its objects are written.
- `LBRemoteSyncManager<T>` — two-way manager: uploads one object at a time and stops at the first failure.
  `pushBeforePull = true` runs upload → download (`LBSyncManager.uploadBeforeDownload`), so an upload failure skips
  the download.

Both managers are final and extend `LBSyncManager` directly; they share the download buffering through an internal
collaborator, so neither exposes engine hooks to override.

Every instance takes its `syncKey` in the constructor: several instances of the same class would otherwise share
the class-name default key, and so one cursor.

## Tests

`commonTest` drives the managers through `LBSyncOperator` with an in-memory remote, store and cursor store
(`./gradlew :synchronization-remote:jvmTest`).

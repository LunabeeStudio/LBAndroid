# :synchronization-remote

Backend-agnostic synchronization managers layered on `:synchronization-core`. KMP (`commonMain`, JVM + iOS
targets, no Android target, like the core). Classes live under `studio.lunabee.synchronization.remote`.

## Why this module exists

`:synchronization-parse-room` ties its managers to the Parse Android SDK and to Android. This module keeps only the
part every remote↔local mapping shares, so a consumer on any platform plugs its own transport (Ktor, a REST client,
a Parse REST wrapper, …) and its own store (a Room DAO, a datasource over one) behind two small interfaces. It has no
Ktor, Parse or Room dependency.

## Contents

- `LBPullRemoteDataSource<T>` — one page of objects since a cursor (`fetchPage(page, updatedAfter)` →
  `LBRemotePage`): strict `updatedAt > cursor`, ascending `updatedAt` with a stable tie-breaker. The implementation
  maps the remote records to the local model `T`.
- `LBSyncRemoteDataSource<T>` — adds the upload of one object: `findServerId(obj)` (e.g. by external id), then
  `update(serverId, obj)` or `create(obj)`.
- `LBRemotePage<T>` — the page objects, `isLastPage`, and a page-level `maxUpdatedAt` that also counts the records
  left out of `objects` (they still move the cursor, through `FetchPage.maxUpdatedAt`).
- `LBPullLocalDataSource<T>` / `LBSyncLocalDataSource<T>` — the local store: `savePulled` (a whole download in one
  call, leaving out the objects still to upload), `clear`, and for two-way managers `objectsToPush` and
  `markPushed` (conditional: a change made during the upload stays to upload).
- `LBRemotePullSyncManager<T>` — download-only manager. Pages are buffered and saved once the last page is read, so a
  failure while paging writes nothing and the store can dedupe across pages; the cursor moves after that write, only
  when something came back. `supportIncrementalSync()` is `final false`: a mid-paging checkpoint would save a cursor
  before its objects are written.
- `LBRemoteSyncManager<T>` — two-way manager: uploads one object at a time and stops at the first failure.
  `pushBeforePull = true` runs upload → download (`LBSyncManager.uploadBeforeDownload`), so an upload failure skips
  the download.

Every instance takes its `syncKey` in the constructor: several instances of the same class would otherwise share
the class-name default key, and so one cursor.

## Tests

`commonTest` drives the managers through `LBSyncOperator` with an in-memory remote, store and cursor store
(`./gradlew :synchronization-remote:jvmTest`).

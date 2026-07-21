# synchronization-core

Coroutine-native, storage-agnostic synchronization engine. Publishes as
`studio.lunabee.synchronization:synchronization-core`.

Three layers, top to bottom:

- **`LBSyncOperator`** — app-wide singleton registry of groups. Runs groups **sequentially**, listens to
  network / app-lifecycle events to trigger refreshes.
- **`LBSyncGroup`** — a set of managers synchronized **in parallel**. Model table dependencies by putting
  the dependency in an earlier group. A suspend `isEnabled` gate can disable a whole group.
- **`LBSyncManager<ServerData, LocalData, PageInfo>`** — abstract per-entity engine running the
  download → upload → re-download pipeline. Subclasses implement the fetch/push SPI.

Run scheduling (single-flight, collapse-and-join, automatic retry) is delegated to the pure
`commonMain` deep module **`SyncRunner`**. Sync cursors are persisted through the
`SyncTimestampLocalDataSource` interface, resolved process-wide via `LBSyncStorage.install(...)`
(see the backend modules `synchronization-core-datastore` / `synchronization-core-room`).

```mermaid
flowchart TD
    OP[LBSyncOperator] -->|sequential| G1[LBSyncGroup 1]
    OP -->|sequential| G2[LBSyncGroup 2]
    G1 -->|parallel| M1[LBSyncManager A]
    G1 -->|parallel| M2[LBSyncManager B]
    G2 -->|parallel| M3[LBSyncManager C]
    M1 --> R1[SyncRunner]
    M1 -->|cursors| S[(SyncTimestampLocalDataSource\nvia LBSyncStorage)]
    R1 -->|runs| P[pipeline: download → upload → re-download]
```

## `LBSyncManager` pipeline

One `suspend` entry point: `synchronize(): LBResult<Unit>`. The pipeline downloads every page, uploads
pending local objects, then re-downloads (unless the server pushes change notifications). Status is
exposed as `status: StateFlow<LBSyncProcessStatus>`; only the engine mutates it.

```mermaid
sequenceDiagram
    participant Caller
    participant M as LBSyncManager
    participant R as SyncRunner
    participant Sub as Subclass SPI
    participant Store as SyncTimestampLocalDataSource

    Caller->>M: synchronize()
    M->>R: run { runPipeline() }
    activate R
    Note over R: launched in the injected scope,<br/>detached from the caller

    rect rgb(235, 244, 255)
        Note over M,Store: download()
        M-->>M: status = DownloadStarted
        M->>Store: lastServerSyncDate(syncKey)
        Store-->>M: cursor (or null)
        loop every page
            M->>Sub: fetchRequest(page, cursor, sinceLastDate)
            Sub-->>M: FetchPage
            M->>Sub: updateData(objects)
            opt supportIncrementalSync()
                M->>Store: saveSyncDates(max updatedAt, now)
            end
            M-->>M: status = DownloadUpdated
        end
        M-->>M: status = DownloadFinishSuccessfully
        M->>Store: saveSyncDates(max updatedAt, now)
    end

    rect rgb(235, 255, 240)
        Note over M,Sub: upload()
        M->>Sub: objectToBeUploaded()
        Sub-->>M: pending local objects
        opt objects not empty
            M-->>M: status = UploadStarted
            M->>Sub: pushObjectsToServer(objects)
            M-->>M: status = UploadFinishSuccessfully
        end
    end

    opt uploaded && !supportChangeNotificationFromServer()
        M->>M: download() again
    end

    M-->>M: status = SyncSuccessfully
    R-->>Caller: LBResult.Success
    deactivate R
```

A thrown error in `fetchRequest` / `pushObjectsToServer` maps to `DownloadFinishWithError` /
`UploadFinishWithError` and the run returns `LBResult.Failure` (the `SyncRunner` then schedules the
automatic retry).

## `SyncRunner` — single-flight collapse-and-join

At most one run is in flight. Callers arriving while a run is in flight collapse into **exactly one**
follow-up run and all receive that follow-up's real result. The follow-up is stored un-launched
(`PendingRun`) and only launched when the in-flight run settles.

```mermaid
sequenceDiagram
    participant A as Caller A
    participant B as Caller B
    participant C as Caller C
    participant R as SyncRunner
    participant W as work (block)

    A->>R: run(block)
    Note over R: idle → launch run 1,<br/>inFlight = run 1
    R->>W: block()
    activate W

    B->>R: run(block)
    Note over R: busy → create PendingRun,<br/>pending = follow-up
    C->>R: run(block)
    Note over R: busy → join existing follow-up<br/>(no new run created)

    W-->>R: result 1
    deactivate W
    R-->>A: result 1
    Note over R: settle: promote pending →<br/>launch follow-up, inFlight = run 2
    R->>W: block()
    activate W
    W-->>R: result 2
    deactivate W
    R-->>B: result 2
    R-->>C: result 2
```

### Failure retry

A failed run with no queued follow-up schedules a re-run after `retryDelay` (default 30 s, `null`
disables). The retry's result is discarded — awaiting callers already received the failure. A new
explicit `run()` or a `cancel()` pre-empts a pending retry.

```mermaid
sequenceDiagram
    participant Caller
    participant R as SyncRunner
    participant W as work (block)

    Caller->>R: run(block)
    R->>W: block()
    W-->>R: LBResult.Failure
    R-->>Caller: LBResult.Failure
    Note over R: nothing queued →<br/>schedule retry in retryDelay

    alt retryDelay elapses
        R->>W: block() (retry, result discarded)
    else new run() arrives first
        Note over R: retry cancelled,<br/>explicit run takes over
    else cancel()
        Note over R: retry cancelled
    end
```

`cancel()` also cancels the in-flight run and resolves every awaiter — including collapsed callers
whose follow-up never launched — with an `LBResult.Failure` carrying the cancellation cause; the
runner stays reusable afterwards.

## Global flow — operator and groups

`LBSyncOperator.syncAllManagers()` runs groups sequentially in registration order; each group runs its
managers in parallel (`async`/`awaitAll` — a failing sibling never cancels the others). Failures
aggregate: one failure surfaces as-is, several wrap into `LBSyncAggregateException`.

```mermaid
sequenceDiagram
    participant App
    participant OP as LBSyncOperator
    participant G1 as Group 1
    participant G2 as Group 2
    participant MA as Manager A
    participant MB as Manager B
    participant MC as Manager C

    App->>OP: syncAllManagers()
    OP->>G1: syncManagers()
    G1->>G1: isEnabled()?
    alt gate closed
        G1-->>OP: Failure(LBSyncClosureException)<br/>managers marked Disabled
    else gate open
        par parallel members
            G1->>MA: synchronize()
            MA-->>G1: LBResult
        and
            G1->>MB: synchronize()
            MB-->>G1: LBResult
        end
        G1-->>OP: combined LBResult
    end
    Note over OP: group 2 always attempts,<br/>even if group 1 failed
    OP->>G2: syncManagers()
    G2->>MC: synchronize()
    MC-->>G2: LBResult
    G2-->>OP: combined LBResult
    OP-->>App: Success or (aggregated) Failure
```

### Event-triggered refresh

```mermaid
sequenceDiagram
    participant Sys as System
    participant OP as LBSyncOperator
    participant G as Groups with matching refreshEvent

    Note over OP: initNetworkListener(context) /<br/>initAppLifecycleListener() at startup

    alt reconnection detected
        Sys->>OP: network callback (offline → online)
        OP->>OP: triggerRefresh(InternetIsBack)
    else app enters foreground
        Sys->>OP: ProcessLifecycleOwner onStart
        OP->>OP: triggerRefresh(AppForeground)
        OP->>G: startServerNotificationListeners()
    else app enters background
        Sys->>OP: ProcessLifecycleOwner onStop
        OP->>G: stopServerNotificationListeners()
    end

    Note over OP,G: only groups carrying the event AND whose<br/>per-event min-delay debounce elapsed
    OP->>G: managers set to PendingSync,<br/>groups run sequentially (detached, defaultSyncScope)
```

## Observation

- `LBSyncManager.status: StateFlow<LBSyncProcessStatus>` — collect it; `currentSyncStatus` is a
  read-only alias of `status.value`.
- `LBSyncGroup.statusByKey()` / `LBSyncOperator.statusByKey()` — combined
  `Flow<Map<SyncKey, LBSyncProcessStatus>>`, snapshot of the registry at collection time.
- `isSyncing(): Flow<Boolean>` — `true` while any member status `isProcessing()`. Mind the quirk:
  the mid-pipeline `UploadFinishSuccessfully` / `DownloadFinishSuccessfully` steps count as processing;
  only `Sync*` / `NeverSync` / `Disabled` / `*WithError` are terminal.

## Setup

```kotlin
// 1. Install a cursor-storage backend once at startup (pick one backend module):
LBSyncStorage.install(context.dataStoreSyncTimestampLocalDataSource()) // or roomSyncTimestampLocalDataSource()

// 2. Register groups and init listeners:
LBSyncOperator.groups["main"] = LBSyncGroup(syncManagers = linkedSetOf(myManager))
LBSyncOperator.initNetworkListener(context)
LBSyncOperator.initAppLifecycleListener()

// 3. Seed statuses from persisted cursors (otherwise NeverSync until first sync):
LBSyncOperator.loadAllStatuses()
```

Renaming a manager subclass silently resets its cursor unless `syncKey` is overridden — treat
`syncKey` as a persisted key.

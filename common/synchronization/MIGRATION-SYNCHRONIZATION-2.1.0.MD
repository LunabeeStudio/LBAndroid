Analyze this repository and migrate it from the synchronization 2.0.0 line to 2.1.0, where `LBSyncOperator` becomes the single entry
point for every sync request.

Before editing:

- Read and follow any `AGENTS.md` / repo instructions you find.
- Limit scope to the synchronization migration only. Do not copy unrelated product changes from reference branches.
- Identify every call site that starts a synchronization: `<manager>.synchronize()` and `<group>.syncManagers()`.
- Library code is open-source at https://github.com/LunabeeStudio/LBAndroid

## What changed and why

Before 2.1.0, sync ordering only existed inside `LBSyncOperator.syncAllManagers()`: it ran the registered groups sequentially, but nothing
stopped a consumer from calling `manager.synchronize()` or `group.syncManagers()` directly. A direct call ran immediately, in parallel with
whatever the operator was doing, so modelling a dependency as "put the dependency in an earlier group" silently did not apply to it.

In 2.1.0 those two functions are `internal`. Every request goes through `LBSyncOperator`, which serializes them: a request waits for the
sync already in progress before it starts. Inside one run, groups still run sequentially in registration order and the managers of a group
still run in parallel.

Nothing changes on disk: cursor storage, `LBSyncStorage.install(...)`, `SyncKey`, statuses and the event listeners are untouched, so the
migration does not re-sync anything.

Target migration:

1. Update the synchronization version entries in `libs.versions.toml` to 2.1.0-alpha01 or later, checking Maven Central for the artifacts
   the project actually uses:
    - `studio.lunabee.synchronization:synchronization-core`
    - `studio.lunabee.synchronization:synchronization-events`
    - `studio.lunabee.synchronization:synchronization-core-datastore`
    - `studio.lunabee.synchronization:synchronization-core-room`
    - `studio.lunabee.synchronization:synchronization-parse-room`
    - If the project uses a shared synchronization version alias, update that alias once and reuse it for every synchronization dependency.
      The five artifacts are released in lockstep, so keep them on the same version.
2. If the project already consumes `studio.lunabee:bom`, prefer the BOM over per-artifact versions. From 4.16.0-alpha01 the BOM pins the
   five `studio.lunabee.synchronization` artifacts, so the project can drop its own synchronization versions:
    - bump the BOM to 4.16.0-alpha01 or later (check Maven Central)
    - a versionless dependency needs the BOM in the same scope it is declared in: a module that exposes a synchronization artifact with
      `api(...)` must also declare `api(platform(...))`, otherwise the constraint is not exported and every consumer module fails with
      `Could not find studio.lunabee.synchronization:<artifact>:.` (note the empty version). `implementation(platform(...))` is enough for a
      module that only uses the artifact internally
    - remove the `version` / `version.ref` from every synchronization dependency so the BOM supplies it, e.g.
      `synchronizationCore = { module = "studio.lunabee.synchronization:synchronization-core" }` in `libs.versions.toml`
    - delete the now-unused synchronization version aliases
    - if the project does not use the BOM, skip this step and keep the explicit versions from step 1
3. Build the project to let the compiler list the call sites: every remaining `synchronize()` / `syncManagers()` call now fails with
   `Cannot access 'synchronize': it is internal in 'LBSyncManager'` (same for `syncManagers`).
4. Replace each call site, adding `import studio.lunabee.synchronization.LBSyncOperator` where needed:
    - `myManager.synchronize()` → `LBSyncOperator.sync(manager = myManager)`
    - `myGroup.syncManagers()` → `LBSyncOperator.sync(group = myGroup)`
    - a group looked up by its registration key → `LBSyncOperator.syncGroup(name = "main")`, which returns an `LBResult.Failure` carrying an
      `IllegalArgumentException` when no group is registered under that key
    - a manager looked up by type → `LBSyncOperator.sync<UserSyncManager>()`, same failure when no manager of that type is registered. Use
      it instead of chaining the lookup yourself: replace
      `LBSyncOperator.syncManager<UserSyncManager>()?.let { LBSyncOperator.sync(manager = it) }` with `LBSyncOperator.sync<UserSyncManager>()`,
      which also stops a lookup miss from silently skipping the sync
    - `LBSyncOperator.syncAllManagers()` is unchanged — leave those call sites alone
    - all of these are `suspend` and return `LBResult<Unit>`, exactly like the functions they replace, so the surrounding code does not move
    - one exception: `syncGroup(name)` and `sync<T>()` return a non-null `LBResult<Unit>`, while the lookups they replace
      (`groups[key]?.syncManagers()`, `syncManager<T>()?.synchronize()`) returned `LBResult<Unit>?`. Delete the `?:` fallback that used to
      handle the miss and the `null` branch of any `when` over the result, otherwise the build fails or warns on dead code. A fallback that
      mapped a miss to `LBResult.Success` was hiding a skipped sync and now reports the miss as a failure — that is the intended change, but
      check what the call site does with a failure
5. Migrate the call sites that live **inside** a sync manager subclass — typically a custom `onLiveQueryCreateOrUpdate` override, or a
   listener callback that re-triggers its own manager:
    - `liveQueryScope.launch { synchronize() }` → `liveQueryScope.launch { LBSyncOperator.sync(manager = this@MyParseRoomSyncManager) }`
    - keep the call inside the launched coroutine. It must stay fire-and-forget so it does not run while the manager's own pipeline holds
      the operator's lock
    - the default `LBParseRoomSyncManager.onLiveQueryCreateOrUpdate` already does this; only overrides need editing
6. Never call `LBSyncOperator.sync(...)`, `syncGroup(...)` or `syncAllManagers()` from inside a manager's SPI — `fetchRequest`,
   `pushObjectsToServer`, `updateData`, `objectToBeUploaded`, `clearData`, `startServerNotificationListener`, `stopServerNotificationListener`
   or `isEnabled`. The operator's lock is not reentrant and the calling coroutine already holds it: this deadlocks, it does not throw.
    - if the project has such a call (a manager that chains another manager itself), lift it out: register the two managers in two groups,
      the dependency first, and let the operator's ordering do the chaining
7. Update the tests of the client repo the same way: `manager.synchronize()` is not visible from another module, so tests must call
   `LBSyncOperator.sync(manager = manager)`.
    - a test that started two managers concurrently and asserted they overlap now observes them serialized — fix the expectation, not the
      library
    - `LBSyncOperator.groups` is a process-wide singleton: clear it between tests (`LBSyncOperator.groups.clear()`) if the migrated tests
      start sharing state through it

Behavior to review after the mechanical replacement:

- **A per-manager sync now queues.** Anything that synchronized one manager on its own — a pull-to-refresh on a single screen, a push
  notification handler — waits for an in-flight full run instead of starting immediately. This is the point of the change, but check any
  UI that assumed the request started right away.
- **Automatic retry is still detached.** A failed run is retried by `SyncRunner` outside the operator's lock (it cannot take that lock
  without deadlocking against the collapsed follow-up run), so a retry can still run while another group is syncing. Set `retryTempo = null`
  on a manager whose retry must not run out of order, and retry at the operator level instead.
- **Retry pre-emption still works.** Enqueueing a request cancels the pending retry of the managers it targets immediately, before it waits
  for its turn, so a parked retry cannot fire in front of a request that is already queued.
- **Concurrent requests still collapse.** Two requests for the same manager still collapse into a single follow-up run whose real
  `LBResult` both callers receive; the operator lock only serializes the requests above that.

Unchanged public API — do not touch these:

- `LBSyncOperator.syncAllManagers()`, `groups`, `registerEventListeners(...)`, `loadAllStatuses()`, `resetAllTimestamps()`,
  `resetAllData()`, `resetAllSyncStatus()`, `cancelAllRequests()`, `hasSomethingToUpload()`, `statusByKey()`, `isSyncing()`,
  `syncManager<T>()`, `syncManagers()` (the registry accessor returning `List<LBGenericSyncManager>`, not a sync trigger)
- `LBSyncGroup.syncManagers` (the property), `refreshEvents`, `isEnabled`, `resetAllTimestamps()`, `resetAllData()`,
  `cancelAllRequests()`, `hasSomethingToUpload()`, `statusByKey()`, `isSyncing()`
- `LBSyncManager.status`, `currentSyncStatus`, `syncKey`, `retryTempo`, `load()`, `resetData()`, `resetTimeStamp()`,
  `resetSyncStatus()`, `lastSuccessfulSyncDate()`, `cancelAllRequests()`, `hasSomethingToUpload()` and the whole subclass SPI
- `LBSyncStorage.install(...)` and the backend factories

Implementation guidance:

- Prefer small, repetitive-safe edits: this migration is a call-site rename plus a handful of judgement calls.
- Preserve existing style, naming, and DI conventions.
- When a multiline call supports named arguments, use named arguments.
- Do not introduce unrelated behavior changes.
- If a call site cannot be migrated without restructuring (a manager that triggers another manager from inside its own SPI), pause and
  explain the case instead of guessing.

Verification:

- Run a real Gradle build covering every touched module.
- Run the test suites that exercise synchronization.
- Run detekt after the migration. If repo instructions specify an exact detekt command, use that exact command.
- Fix warnings/errors related to the migration instead of suppressing them.
- Check where the chosen version actually resolves from. A pre-release that only exists in the local Maven repository builds on the machine
  that published it and fails everywhere else, so it has to be published before the branch can go through CI.

In your final summary, include:

- the synchronization version the project now depends on, and whether it comes from the BOM or from explicit versions
- every migrated call site, grouped by replacement (`sync(manager)`, `sync<T>()`, `sync(group)`, `syncGroup(name)`)
- any `?:` fallback or `null` branch dropped because the replacement returns a non-null `LBResult`, and whether that changed what the call
  site reports on a miss
- any call site that was inside a manager's own SPI and how it was restructured
- the tests that had to change because requests are now serialized
- whether any manager needed `retryTempo = null`
- build, test and detekt results

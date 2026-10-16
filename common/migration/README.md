# :migration-core / :migration-datastore

Versioned, step-by-step app data migrations. KMP, classes live under `studio.lunabee.migration`
(`studio.lunabee.migration.datastore` for the DataStore store).

- `:migration-core` (JVM, iOS, macOS, Linux, Windows): the engine, depends on `:core` and coroutines only.
- `:migration-datastore` (JVM, iOS, macOS, Linux): an `LBMigrationVersionStore` backed by a `DataStore<Preferences>`.

## Setup

Versions come from the BOM:

```kotlin
implementation(platform("studio.lunabee:bom:<version>"))
implementation("studio.lunabee.migration:migration-core")
implementation("studio.lunabee.migration:migration-datastore") // optional
```

## Declare the steps

A step moves the data from `startVersion` to `endVersion` and throws on failure. `Input` is whatever the steps need
(`Unit` when nothing):

```kotlin
class MigrationV0ToV1(private val repository: Repository) : LBMigration<Unit> {
    override val startVersion: Int = 0
    override val endVersion: Int = 1

    override suspend fun migrate(input: Unit) {
        repository.renameColumn()
    }
}
```

## Run

`LBMigrationRunner` is a plain class and can be a DI singleton. Each `startVersion` must be unique and each
`endVersion` greater than its `startVersion`, otherwise the constructor throws `IllegalArgumentException`.

```kotlin
val runner = LBMigrationRunner(
    migrations = listOf(MigrationV0ToV1(repository), MigrationV1ToV2(repository)),
    lastVersion = 2,
)
```

Call it from your own use case:

```kotlin
when (val result = runner.run(input = Unit, versionStore = versionStore)) {
    is LBResult.Success -> Unit
    is LBResult.Failure -> logger.e(result.throwable) { "Migration failed" } // or fail the login
}
```

`run` reads the stored version, then for each version below `lastVersion` runs the step starting there and saves its
`endVersion`. It stops at the first failing step without saving that step's version, so the next `run` retries it, and
returns `LBResult.Failure` carrying the step exception. A `CancellationException` is rethrown. A stored version at or
above `lastVersion` runs nothing and returns `Success`.

When no step starts at the current version, the `Failure` carries `LBMissingMigrationException`, with the version in
`fromVersion`.

## Version store

Implement `LBMigrationVersionStore` (`getVersion()` / `setVersion(version)`) over your own storage, e.g. one per user.
There is no "no stored version" in the library: `getVersion` returns the version to start from.

With `:migration-datastore`, pass your own DataStore:

```kotlin
val versionStore = DataStoreMigrationVersionStore(
    dataStore = dataStore,
    key = "lbMigrationVersion",
    versionWhenAbsent = 0,
)
```

- `key` lets several scopes share one DataStore (one store instance per key).
- `versionWhenAbsent` is the fresh-install choice: `0` runs every step, `lastVersion` skips them all.

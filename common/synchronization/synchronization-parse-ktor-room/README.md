# :synchronization-parse-ktor-room

Parse to Room synchronization in common code (Android + iOS), built on `parse-ktor` and `:synchronization-remote`.
Classes live under `studio.lunabee.synchronization.parsektorroom`.

It supersedes `:synchronization-parse-room`, whose managers are Android-only and bound to the Parse Android SDK. Out
of scope: LiveQuery, hard deletes (no `DELETE` is ever sent) and `ParseUser` sync (keep it app-side).

## Dependency

```kotlin
dependencies {
    implementation(platform("studio.lunabee:bom:<bom-version>"))
    implementation("studio.lunabee.synchronization:synchronization-parse-ktor-room")
}
```

It exposes `parse-ktor`, `synchronization-remote` and `synchronization-core-room` (`LBRoomSyncModel`,
`LBRoomSyncDao`, the Room runtime) as `api`. Install a cursor store at startup, as for any manager
(`LBSyncStorage.install(…)`).

## Declaring a class

```kotlin
@Serializable
data class CoachDto(
    override val objectId: ParseObjectId? = null,
    override val updatedAt: Instant? = null,
    val name: String,
    val localId: String? = null,
    val deleted: Boolean = false,
) : ParseObject

@Entity
data class CoachEntity(
    @PrimaryKey override val lbLocalId: String,
    override val lbServerId: String?,
    override val lbUpdatedAt: Instant?,
    override val lbInSync: Boolean,
    override val lbDeleted: Boolean,
    val name: String,
) : LBRoomSyncModel

@Dao
abstract class CoachDao : LBRoomSyncDao<CoachEntity>() {
    @Query("SELECT * FROM CoachEntity WHERE lbInSync = 0")
    abstract override suspend fun notInSync(): List<CoachEntity>

    @Query("UPDATE CoachEntity SET lbInSync = 1 WHERE lbLocalId IN (:localIds)")
    abstract override suspend fun markInSync(localIds: List<String>)

    @Query("DELETE FROM CoachEntity")
    abstract override suspend fun deleteAll()
}

object CoachClass : LBParseRoomClass<CoachDto, CoachEntity>(CoachDto.serializer()) {
    override fun tableParseName(): String = "Coach"

    override val localIdKey: String = "localId"

    override fun createObjectFrom(serverObject: CoachDto): CoachEntity = CoachEntity(
        lbLocalId = serverObject.localId ?: checkNotNull(serverObject.objectId).value,
        lbServerId = serverObject.objectId?.value,
        lbUpdatedAt = serverObject.updatedAt,
        lbInSync = true,
        lbDeleted = serverObject.deleted,
        name = serverObject.name,
    )

    override fun update(from: CoachEntity): CoachDto = CoachDto(
        name = from.name,
        localId = from.lbLocalId,
        deleted = from.lbDeleted,
    )
}
```

`D` is bounded by `ParseObject`: the keyset cursor needs each object's `objectId` and `updatedAt`. Optional members:
`keysToSelect()` (default: all fields; a restricted list must include `localIdKey` and the soft-delete field,
otherwise a pushed row comes back as a duplicate and tombstones are lost) and `keysToInclude()` (pointers fetched in place, default none).
`createObjectFrom` must return the row with `lbInSync = true`.

## Managers

```kotlin
val pull = CoachClass.pullManager(syncKey = SyncKey("Coach"), parse = parseClient, dao = coachDao)
val sync = CoachClass.syncManager(syncKey = SyncKey("Coach"), parse = parseClient, dao = coachDao)
```

- `pullManager` downloads only, an `LBRemotePullSyncManager`. Most classes need nothing else.
- `syncManager` also pushes the rows in `notInSync()` (`pushBeforePull = true` pushes first), an
  `LBRemoteSyncManager`. It throws `IllegalArgumentException` when `localIdKey` is `null`.

Both pull by keyset (`updatedAt`, then `objectId`), never by `skip`, so a record updated during the download is not
skipped.

## Push

- `localIdKey` is the Parse column storing the local id. `update` must write it. A row without `lbServerId` is first
  looked up with `GET classes/<C>` on `where {<localIdKey>: lbLocalId}` and `limit 1`: when found it is sent as `PUT`
  to that `objectId`, otherwise as `POST`. The lookup prevents a duplicate when a row is pushed twice before it is
  pulled back, or when a create succeeded but its response was lost. A row with `lbServerId` is sent as `PUT` with no
  lookup.
- The body is the JSON of `update(row)` without the reserved `objectId`, `createdAt` and `updatedAt`. Fields equal to
  their default value and `null` fields are sent, so a field can be reset or cleared.
- Soft delete only: `update` maps `lbDeleted` to the app's own Parse field, and a deleted row is pushed as any other.
  Pull brings tombstones back as `lbDeleted = true`. No `DELETE` is ever sent.
- A failing request fails the run with the error mapped by `LBParseConfig.mapErr` (an `LBParseException` by default).
  The rows already pushed stay in sync, the failing row and the next ones stay in `notInSync()`.

## Local edits

- A pulled row whose `lbLocalId` is currently in `notInSync()` is not written, so a pull never overwrites a local edit
  not pushed yet. The other rows of the same pull are.
- Known gap: after a push, the row is marked in sync unconditionally (`markInSync(listOf(lbLocalId))`). An edit made
  while the row was uploading is marked in sync too, and is pushed with the next edit of the row.

## Moving from `synchronization-parse-room`

`LBRoomSyncModel` and `LBRoomSyncDao` now live in `synchronization-core-room` with the same FQNs: entities and DAOs
need no change. Pass `syncKey = SyncKey("<old manager class simple name>")` to keep the stored cursor: it has the same
meaning (max `updatedAt`), so the switch triggers no full resync. The member names of `LBParseRoomClass` follow
`LBParseRoomSyncManager`.

## Tests

`commonTest` runs the managers through `LBSyncOperator` on `FakeParseServer` (`parse-ktor-test`), an in-memory DAO and
cursor store, as Android host tests and on the iOS simulator
(`./gradlew :synchronization-parse-ktor-room:testAndroidHostTest :synchronization-parse-ktor-room:iosSimulatorArm64Test`).

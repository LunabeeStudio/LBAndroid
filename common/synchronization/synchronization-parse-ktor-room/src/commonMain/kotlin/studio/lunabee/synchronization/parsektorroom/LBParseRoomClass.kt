/*
 * Copyright (c) 2026 Lunabee Studio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package studio.lunabee.synchronization.parsektorroom

import kotlinx.serialization.KSerializer
import studio.lunabee.parse.client.LBParseClient
import studio.lunabee.parse.query.ParseObject
import studio.lunabee.synchronization.parsektorroom.local.LBRoomSyncLocalDataSource
import studio.lunabee.synchronization.parsektorroom.remote.LBParseRemoteDataSource
import studio.lunabee.synchronization.remote.LBRemotePullSyncManager
import studio.lunabee.synchronization.remote.LBRemoteSyncManager
import studio.lunabee.synchronization.roomsyncmanager.LBRoomSyncDao
import studio.lunabee.synchronization.roomsyncmanager.LBRoomSyncModel
import studio.lunabee.synchronization.store.SyncKey

/**
 * Declaration of a Parse class synced with a Room table: which class to read, how to map it to a row and, for a class
 * that is pushed, how to write a row back.
 *
 * @param D the `@Serializable` DTO of the Parse class.
 * @param R the Room row.
 * @param serializer serializer of [D].
 */
abstract class LBParseRoomClass<D : ParseObject, R : LBRoomSyncModel>(
    internal val serializer: KSerializer<D>,
) {
    /**
     * Parse field storing the local id of a row, required to push. `null` for a class that is only pulled.
     */
    open val localIdKey: String? = null

    /** Name of the Parse class. */
    abstract fun tableParseName(): String

    /**
     * Parse fields to read, every field when empty. A restricted list must include [localIdKey] and the soft-delete
     * field of the class: without [localIdKey], [createObjectFrom] cannot recover the local id, so a pushed row comes
     * back as a duplicate, and without the soft-delete field tombstones are lost.
     */
    open fun keysToSelect(): List<String> = emptyList()

    /** Parse pointer fields to fetch in place, none by default. */
    open fun keysToInclude(): List<String> = emptyList()

    /**
     * Maps [serverObject] to a Room row. The row must be in sync (`lbInSync = true`). Its `lbLocalId` comes from the
     * [localIdKey] field for a class that is pushed, or from `objectId` for a row the server created.
     */
    abstract fun createObjectFrom(serverObject: D): R

    /**
     * Body sent to Parse when [from] is pushed. It must write the [localIdKey] field and the soft-delete field of the
     * class from `lbDeleted`. The reserved `objectId`, `createdAt` and `updatedAt` fields are dropped from the body.
     */
    open fun update(from: R): D = throw UnsupportedOperationException(
        "update() must be overridden to push a row",
    )

    /**
     * Download-only manager of this class.
     *
     * @param syncKey persisted key of the cursor, reuse the key of a previous manager to keep its cursor.
     * @param parse client of the Parse server.
     * @param dao the Room table.
     * @param logging enables the manager logs.
     */
    fun pullManager(
        syncKey: SyncKey,
        parse: LBParseClient,
        dao: LBRoomSyncDao<R>,
        logging: Boolean = true,
    ): LBRemotePullSyncManager<R> = LBRemotePullSyncManager(
        syncKey = syncKey,
        remoteDataSource = LBParseRemoteDataSource(parse = parse, declaration = this),
        localDataSource = LBRoomSyncLocalDataSource(dao = dao),
        logging = logging,
    )

    /**
     * Two-way manager of this class: it pulls, and pushes the rows not in sync. Rows are never deleted on the server,
     * a deleted row is pushed with its soft-delete flag.
     *
     * @param syncKey persisted key of the cursor, reuse the key of a previous manager to keep its cursor.
     * @param parse client of the Parse server.
     * @param dao the Room table.
     * @param pushBeforePull `true` to push before pulling.
     * @param logging enables the manager logs.
     * @throws IllegalArgumentException when [localIdKey] is `null`.
     */
    fun syncManager(
        syncKey: SyncKey,
        parse: LBParseClient,
        dao: LBRoomSyncDao<R>,
        pushBeforePull: Boolean = false,
        logging: Boolean = true,
    ): LBRemoteSyncManager<R> {
        require(localIdKey != null) { "localIdKey must be set to push a row" }
        return LBRemoteSyncManager(
            syncKey = syncKey,
            remoteDataSource = LBParseRemoteDataSource(parse = parse, declaration = this),
            localDataSource = LBRoomSyncLocalDataSource(dao = dao),
            pushBeforePull = pushBeforePull,
            logging = logging,
        )
    }
}

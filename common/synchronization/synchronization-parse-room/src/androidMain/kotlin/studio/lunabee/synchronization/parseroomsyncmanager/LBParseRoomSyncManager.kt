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

package studio.lunabee.synchronization.parseroomsyncmanager

import com.parse.ParseException
import com.parse.ParseObject
import com.parse.ParseQuery
import com.parse.coroutines.getById
import com.parse.coroutines.suspendFind
import com.parse.coroutines.suspendSave
import com.parse.livequery.SubscriptionHandling
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import studio.lunabee.synchronization.roomsyncmanager.LBRoomSyncDao
import studio.lunabee.synchronization.roomsyncmanager.LBRoomSyncManager
import studio.lunabee.synchronization.syncmanager.FetchPage
import java.util.Date
import kotlin.time.Instant

typealias LBGenericParseRoomSyncManager = LBParseRoomSyncManager<*>

/**
 * Abstract subclass of [LBRoomSyncManager] specific for entities saved in Room and coming from a
 * Parse server. Extension surface: implement [tableParseName], [update] and (from the base)
 * `createObjectFrom`.
 *
 * [objectToBeUploaded] returns full immutable entities, so [push] receives the entity directly and
 * never re-queries it by primary key.
 *
 * @param RoomData The Room entity type to be mapped from [ParseObject]
 * @param dao The persistence base for [RoomData]
 * @param queryDispatcher Coroutine dispatcher used for read accesses to the local data
 * @param writeDispatcher Coroutine dispatcher used for write accesses to the local data
 */
abstract class LBParseRoomSyncManager<RoomData : LBParseRoomModel>(
    dao: LBRoomSyncDao<RoomData>,
    logging: Boolean = true,
    queryDispatcher: CoroutineDispatcher = Dispatchers.IO,
    writeDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LBRoomSyncManager<ParseObject, RoomData, Nothing>(dao, logging, queryDispatcher, writeDispatcher) {

    /**
     * Scope used to fire-and-forget a [synchronize] from the LiveQuery callback, detached from any
     * caller.
     */
    private val liveQueryScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    /**
     * The parse query to use for the synchronization and LiveQuery if needed.
     * Must be a unique instance for LiveQuery.
     * Please use this instead of {@code parseQuery()}.
     */
    private var parseQuery: ParseQuery<ParseObject>? = null
        get() {
            if (field == null) {
                field = parseQuery()
            }
            return field
        }

    /**
     * @return the parse table name you want to sync
     * eg : "User"
     */
    protected abstract fun tableParseName(): String

    /**
     * The mapping between the Room entity and the parseObject: what fields you want to set on the
     * parseObject when you push it.
     * **WARNING** This is used for creation and update.
     *
     * @param parseObject the parse object to update
     * @param from the Room entity you want to update from
     */
    protected abstract fun update(parseObject: ParseObject, from: RoomData)

    /**
     * Override this if you want to only select specific keys for the parse query.
     */
    protected open fun keysToSelect(): List<String> = emptyList()

    /**
     * Override this if you want to include objects for the parse query.
     */
    protected open fun keysToInclude(): List<String> = emptyList()

    /**
     * Override this if you want to create a custom parse query.
     * **WARNING** If you just want to select or include keys, @see [keysToSelect] and [keysToInclude].
     */
    protected open fun parseQuery(): ParseQuery<ParseObject> {
        val objectQuery: ParseQuery<ParseObject> = ParseQuery.getQuery(tableParseName())
        keysToInclude().forEach { keyToInclude ->
            objectQuery.include(keyToInclude)
        }
        if (keysToSelect().isNotEmpty()) {
            objectQuery.selectKeys(keysToSelect())
        }
        return objectQuery
    }

    /**
     * Override this if you want to do specific work on live query notification.
     * @param event the live query event, can be used to know if it is a creation or an update
     */
    protected open fun onLiveQueryCreateOrUpdate(
        event: SubscriptionHandling.Event,
        parseObject: ParseObject,
    ) {
        liveQueryScope.launch { synchronize() }
    }

    /**
     * You can activate this option to optimize a sync failure.
     * This requires records fetched to be ordered by ascending updatedAt.
     */
    override fun supportIncrementalSync(): Boolean = true

    override fun updatedAt(obj: ParseObject): Instant? =
        obj.updatedAt?.let { Instant.fromEpochMilliseconds(it.time) }

    /**
     * Override this if you want to support paging.
     * By default set to 100 for Parse Sync Manager.
     */
    override fun queryPageSize(): Int? = 100

    /**
     * How to fetch the data from the server.
     * **WARNING** : Data returned must be ordered by updatedAt.
     *
     * The objects are ordered by updatedAt; the {@code page} and {@code queryPageSize()} fix the
     * limit and skip factors. The query runs through the Parse coroutines artifact's
     * [suspendFind] extension (which dispatches off the main thread); a [ParseException] is thrown so
     * the engine maps it to a download error status.
     *
     * @param page the page number to fetch.
     * @param cursor unused by the Parse limit/skip paging.
     * @param sinceLastDate the last server cursor; the Parse API still takes [java.util.Date].
     * @return the fetched page of [ParseObject]s.
     */
    override suspend fun fetchRequest(
        page: Int,
        cursor: String?,
        sinceLastDate: Instant?,
    ): FetchPage<ParseObject, Nothing> {
        val query = parseQuery()
        query.orderByAscending("updatedAt")
        sinceLastDate?.let {
            query.whereGreaterThan("updatedAt", Date(it.toEpochMilliseconds()))
        }
        queryPageSize()?.let {
            query.limit = it
            query.skip = page * it
        }
        val objects = query.suspendFind()
        return FetchPage(objects = objects)
    }

    /**
     * How to push a Room entity to the server. Throw the [ParseException] on failure.
     *
     * Uses the Parse coroutines artifact's [getById] and [suspendSave] extensions, which dispatch
     * off the main thread, so no explicit dispatcher wrapping is needed. An
     * [ParseException.OBJECT_NOT_FOUND] on the lookup means the object does not exist yet and a fresh
     * one is created; any other [ParseException] is rethrown.
     * @param obj the Room entity to push
     */
    override suspend fun push(obj: RoomData) {
        val serverId = obj.lbServerId
        val query: ParseQuery<ParseObject> = parseQuery()

        val result = try {
            if (serverId != null) query.getById(serverId) else null
        } catch (e: ParseException) {
            if (e.code != ParseException.OBJECT_NOT_FOUND) {
                throw e
            } else {
                null
            }
        }

        val objToSave = result ?: ParseObject.create(tableParseName())
        update(objToSave, obj)
        objToSave.suspendSave()
    }

    override suspend fun startServerNotificationListener(): Boolean {
        LBParseLiveQueryManager.instance.unsubscribe(parseQuery!!)
        val subscriptionHandling = LBParseLiveQueryManager.instance.subscribe(parseQuery!!)
        subscriptionHandling?.let {
            it.handleEvents { _, event, `object` ->
                if (event == SubscriptionHandling.Event.CREATE || event == SubscriptionHandling.Event.UPDATE) {
                    onLiveQueryCreateOrUpdate(event, `object`)
                }
            }
        }
        return subscriptionHandling != null
    }

    override suspend fun stopServerNotificationListener(): Boolean {
        LBParseLiveQueryManager.instance.unsubscribe(parseQuery!!)
        parseQuery = null
        return true
    }
}

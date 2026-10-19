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

import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import studio.lunabee.parse.test.FakeParseServer
import studio.lunabee.parse.test.assertQuery
import studio.lunabee.parse.test.assertRequest
import studio.lunabee.parse.test.respondParse
import studio.lunabee.synchronization.LBSyncOperator
import studio.lunabee.synchronization.store.SyncKey
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private const val PageSize: Int = 100

class LBParseRoomPullTest {
    private val syncKey = SyncKey("items")
    private lateinit var cursors: FakeSyncTimestampLocalDataSource

    @BeforeTest
    fun installCursorStore() {
        cursors = FakeSyncTimestampLocalDataSource()
    }

    @Test
    fun pull_requests_the_class_by_ascending_update_with_keys_include_and_the_stored_cursor() = runTest {
        cursors.saveSyncDates(syncKey, serverDate = Instant.parse("2026-09-20T08:00:00.000Z"), localDate = null)
        val server = FakeParseServer(handler = { respondParse(resultsBody(IntRange.EMPTY)) })

        LBSyncOperator.sync(manager = TestClass().pullManager(syncKey, server.client, FakeRoomDao()))

        assertRequest(server.requests.first(), HttpMethod.Get, "classes/Item")
        assertQuery(
            server.requests.first(),
            where = """{"updatedAt":{"${'$'}gt":{"__type":"Date","iso":"2026-09-20T08:00:00.000Z"}}}""",
            order = "updatedAt,objectId",
            limit = PageSize,
        )
        assertEquals("name,localId,deleted", server.requests.first().url.parameters["keys"])
        assertEquals("owner", server.requests.first().url.parameters["include"])
    }

    @Test
    fun a_three_page_pull_follows_the_keyset_cursor_upserts_once_and_stores_the_max_update() = runTest {
        var served = 0
        val server = FakeParseServer(
            handler = {
                val page = served++
                respondParse(
                    when (page) {
                        0 -> resultsBody(1..PageSize)
                        1 -> resultsBody(PageSize + 1..2 * PageSize)
                        else -> resultsBody(2 * PageSize + 1..2 * PageSize + 5)
                    },
                )
            },
        )
        val dao = FakeRoomDao()

        LBSyncOperator.sync(manager = TestClass().pullManager(syncKey, server.client, dao))

        assertEquals(3, server.requests.size)
        assertQuery(
            server.requests[1],
            where = """{"${'$'}or":[
                {"updatedAt":{"${'$'}gt":{"__type":"Date","iso":"2026-09-21T10:00:00.100Z"}}},
                {"updatedAt":{"__type":"Date","iso":"2026-09-21T10:00:00.100Z"},"objectId":{"${'$'}gt":"o100"}}
            ]}""",
            order = "updatedAt,objectId",
            limit = PageSize,
        )
        assertEquals(1, dao.upserts.size)
        assertEquals((1..2 * PageSize + 5).map { "o" + it.toString().padStart(3, '0') }, dao.upserts.single().map { it.lbLocalId })
        assertEquals(Instant.parse("2026-09-21T10:00:00.205Z"), cursors.lastServerSyncDate(syncKey))
    }

    @Test
    fun a_pulled_row_still_to_push_is_not_upserted_but_the_others_are() = runTest {
        val server = FakeParseServer(handler = { respondParse(resultsBody(1..3)) })
        val pending = TestRow(lbLocalId = "o002", name = "local edit")
        val dao = FakeRoomDao(rows = listOf(pending))

        LBSyncOperator.sync(manager = TestClass().pullManager(syncKey, server.client, dao))

        assertEquals(listOf("o001", "o003"), dao.upserts.single().map { it.lbLocalId })
        assertEquals(pending, dao.rows.single { it.lbLocalId == "o002" })
    }

    @Test
    fun clearing_the_manager_deletes_every_row() = runTest {
        val dao = FakeRoomDao(rows = listOf(TestRow(lbLocalId = "a")))

        TestClass().pullManager(syncKey, FakeParseServer().client, dao).resetData()

        assertEquals(1, dao.deleteAllCalls)
    }
}

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

import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import studio.lunabee.core.model.LBResult
import studio.lunabee.parse.error.LBParseException
import studio.lunabee.parse.test.FakeParseServer
import studio.lunabee.parse.test.assertJsonBody
import studio.lunabee.parse.test.assertQuery
import studio.lunabee.parse.test.assertRequest
import studio.lunabee.parse.test.respondParse
import studio.lunabee.parse.test.respondParseError
import studio.lunabee.synchronization.LBSyncOperator
import studio.lunabee.synchronization.remote.LBRemoteSyncManager
import studio.lunabee.synchronization.store.SyncKey
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class LBParseRoomSyncTest {
    private val syncKey = SyncKey("items")

    @BeforeTest
    fun installCursorStore() {
        FakeSyncTimestampLocalDataSource()
    }

    @Test
    fun a_row_with_a_server_id_is_put_to_it_without_lookup() = runTest {
        val server = server()
        val dao = FakeRoomDao(
            rows = listOf(
                TestRow(lbLocalId = "a", lbServerId = "srv-a", name = "x", lbUpdatedAt = Instant.parse("2026-09-21T10:00:00Z")),
            ),
        )

        LBSyncOperator.sync(manager = manager(server, dao))

        val pushes = pushRequests(server)
        assertEquals(1, pushes.size)
        assertRequest(pushes.single(), HttpMethod.Put, "classes/Item/srv-a")
        assertJsonBody(pushes.single(), """{"name":"x","localId":"a","deleted":false,"count":0}""")
    }

    @Test
    fun a_row_without_server_id_is_looked_up_then_put_when_found() = runTest {
        val server = server(known = mapOf("a" to "srv-a"))

        LBSyncOperator.sync(manager = manager(server, FakeRoomDao(rows = listOf(TestRow(lbLocalId = "a", name = "x")))))

        val requests = pushRequests(server)
        assertRequest(requests[0], HttpMethod.Get, "classes/Item")
        assertQuery(requests[0], where = """{"localId":"a"}""", order = null, limit = 1)
        assertEquals("objectId", requests[0].url.parameters["keys"])
        assertRequest(requests[1], HttpMethod.Put, "classes/Item/srv-a")
    }

    @Test
    fun a_row_without_server_id_is_looked_up_then_posted_when_missing() = runTest {
        val server = server()

        LBSyncOperator.sync(manager = manager(server, FakeRoomDao(rows = listOf(TestRow(lbLocalId = "a", name = "x")))))

        val requests = pushRequests(server)
        assertEquals(listOf(HttpMethod.Get, HttpMethod.Post), requests.map { it.method })
        assertRequest(requests[1], HttpMethod.Post, "classes/Item")
        assertJsonBody(requests[1], """{"name":"x","localId":"a","deleted":false,"count":0}""")
    }

    @Test
    fun a_field_set_to_its_default_value_is_present_in_the_body() = runTest {
        val server = server()
        val dao = FakeRoomDao(rows = listOf(TestRow(lbLocalId = "a", lbServerId = "srv-a")))

        LBSyncOperator.sync(manager = manager(server, dao))

        assertJsonBody(pushRequests(server).single(), """{"name":"","localId":"a","deleted":false,"count":0}""")
    }

    @Test
    fun push_before_pull_sends_the_push_before_the_pull() = runTest {
        val server = server()
        val dao = FakeRoomDao(rows = listOf(TestRow(lbLocalId = "a", lbServerId = "srv-a")))

        LBSyncOperator.sync(manager = manager(server, dao, pushBeforePull = true))

        assertEquals(listOf(HttpMethod.Put, HttpMethod.Get), server.requests.map { it.method })
        assertTrue(server.requests[1].isPull())
    }

    @Test
    fun every_pushed_row_is_marked_in_sync() = runTest {
        val dao = FakeRoomDao(rows = listOf(TestRow(lbLocalId = "a"), TestRow(lbLocalId = "b")))

        LBSyncOperator.sync(manager = manager(server(), dao))

        assertTrue(dao.rows.all { it.lbInSync })
    }

    @Test
    fun a_push_failure_fails_the_run_with_the_mapped_error_and_keeps_the_rest_to_push() = runTest {
        val dao = FakeRoomDao(
            rows = listOf(
                TestRow(lbLocalId = "a", lbServerId = "srv-a"),
                TestRow(lbLocalId = "b", lbServerId = "srv-b"),
                TestRow(lbLocalId = "c", lbServerId = "srv-c"),
            ),
        )

        val result = LBSyncOperator.sync(manager = manager(server(failOn = "srv-b"), dao))

        val failure = assertIs<LBResult.Failure<*>>(result)
        assertEquals(141, assertIs<LBParseException.Parse>(failure.throwable).code)
        assertEquals(listOf("a"), dao.rows.filter { it.lbInSync }.map { it.lbLocalId })
        assertEquals(listOf("b", "c"), dao.notInSync().map { it.lbLocalId })
    }

    @Test
    fun a_deleted_row_is_pushed_as_an_update_and_never_deleted() = runTest {
        val server = server()
        val dao = FakeRoomDao(rows = listOf(TestRow(lbLocalId = "a", lbServerId = "srv-a", lbDeleted = true, name = "x")))

        LBSyncOperator.sync(manager = manager(server, dao))

        val pushes = pushRequests(server)
        assertEquals(listOf(HttpMethod.Put), pushes.map { it.method })
        assertJsonBody(pushes.single(), """{"name":"x","localId":"a","deleted":true,"count":0}""")
        assertTrue(server.requests.none { it.method == HttpMethod.Delete })
    }

    @Test
    fun a_sync_manager_without_local_id_key_is_rejected() {
        assertFailsWith<IllegalArgumentException> {
            TestClass(localIdKey = null).syncManager(syncKey, FakeParseServer().client, FakeRoomDao())
        }
    }

    private fun HttpRequestData.isPull(): Boolean = method == HttpMethod.Get && url.parameters["order"] != null

    private fun HttpRequestData.isLookup(): Boolean = method == HttpMethod.Get && url.parameters["order"] == null

    private fun pushRequests(server: FakeParseServer): List<HttpRequestData> = server.requests.filterNot { it.isPull() }

    private fun server(
        known: Map<String, String> = emptyMap(),
        failOn: String? = null,
    ): FakeParseServer = FakeParseServer(
        handler = { request -> answer(request, known, failOn) },
    )

    private fun MockRequestHandleScope.answer(
        request: HttpRequestData,
        known: Map<String, String>,
        failOn: String?,
    ): HttpResponseData = when {
        request.isPull() -> respondParse(resultsBody(IntRange.EMPTY))

        request.isLookup() -> {
            val localId = Json.parseToJsonElement(
                checkNotNull(request.url.parameters["where"]),
            ).jsonObject.getValue("localId").jsonPrimitive.content
            respondParse(known[localId]?.let { """{"results":[{"objectId":"$it"}]}""" } ?: """{"results":[]}""")
        }

        failOn != null && request.url.encodedPath.endsWith(
            failOn,
        ) -> respondParseError(code = 141, error = "boom", status = HttpStatusCode.InternalServerError)

        else -> respondParse("""{"objectId":"created"}""")
    }

    private fun manager(
        server: FakeParseServer,
        dao: FakeRoomDao,
        pushBeforePull: Boolean = false,
    ): LBRemoteSyncManager<TestRow> = TestClass()
        .syncManager(syncKey, server.client, dao, pushBeforePull = pushBeforePull)
        .apply { retryTempo = null }
}

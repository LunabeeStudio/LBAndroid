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

package studio.lunabee.parse.query

import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import studio.lunabee.parse.test.FakeParseServer
import studio.lunabee.parse.test.assertQuery
import studio.lunabee.parse.test.assertRequest
import studio.lunabee.parse.test.respondParse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class ParseQueryTest {
    @Serializable
    private data class Item(
        override val objectId: String? = null,
        override val updatedAt: Instant? = null,
    ) : ParseObject

    private fun itemsBody(count: Int): String = (1..count).joinToString(prefix = """{"results":[""", postfix = "]}") {
        val id = it.toString().padStart(3, '0')
        """{"objectId":"o$id","updatedAt":"2026-09-21T10:00:00.${id}Z"}"""
    }

    @Test
    fun queryHitsClassPathWithParameters() = runTest {
        val server = FakeParseServer(handler = { respondParse(itemsBody(2)) })

        val items = server.client.query<Item>(
            className = "Coach",
            where = buildJsonObject { put("name", "Ada") },
            order = "name",
            limit = 5,
        )

        assertEquals(2, items.size)
        assertRequest(server.lastRequest, HttpMethod.Get, "classes/Coach")
        assertQuery(server.lastRequest, where = """{"name":"Ada"}""", order = "name", limit = 5)
    }

    @Test
    fun pullFirstPageUsesKeysetOrderAndWhereOnly() = runTest {
        val server = FakeParseServer(handler = { respondParse(itemsBody(3)) })

        val page = server.client.pull<Item, String>(className = "Coach", where = JsonObject(emptyMap()), cursor = null) { it.objectId }

        assertQuery(server.lastRequest, where = "{}", order = PullOrder, limit = PageSize)
        assertEquals(listOf("o001", "o002", "o003"), page.items)
        assertNull(page.nextCursor)
        assertEquals(Instant.parse("2026-09-21T10:00:00.003Z"), page.maxUpdatedAt)
    }

    @Test
    fun fullPageGivesCursorAndNextPageCombinesUpdatedAtAndObjectIdWithOr() = runTest {
        val server = FakeParseServer(handler = { respondParse(itemsBody(PageSize)) })
        val first = server.client.pull<Item, String>(className = "Coach", where = JsonObject(emptyMap()), cursor = null) { it.objectId }

        server.client.pull<Item, String>(className = "Coach", where = JsonObject(emptyMap()), cursor = first.nextCursor) { it.objectId }

        assertEquals("2026-09-21T10:00:00.100Z|o100", first.nextCursor)
        assertQuery(
            server.lastRequest,
            where = """{"${'$'}or":[
                {"updatedAt":{"${'$'}gt":{"__type":"Date","iso":"2026-09-21T10:00:00.100Z"}}},
                {"updatedAt":{"__type":"Date","iso":"2026-09-21T10:00:00.100Z"},"objectId":{"${'$'}gt":"o100"}}
            ]}""",
            order = PullOrder,
            limit = PageSize,
        )
    }

    @Test
    fun cursorKeepsCallerOrByWrappingBothInAnd() = runTest {
        val server = FakeParseServer(handler = { respondParse(itemsBody(1)) })
        val callerWhere = buildJsonObject {
            putJsonArray("\$or") { add(buildJsonObject { put("a", 1) }) }
        }

        server.client.pull<Item, String>(
            className = "Coach",
            where = callerWhere,
            cursor = "2026-09-21T10:00:00.100Z|o100",
        ) { it.objectId }

        assertQuery(
            server.lastRequest,
            where = """{"${'$'}and":[
                {"${'$'}or":[{"a":1}]},
                {"${'$'}or":[
                    {"updatedAt":{"${'$'}gt":{"__type":"Date","iso":"2026-09-21T10:00:00.100Z"}}},
                    {"updatedAt":{"__type":"Date","iso":"2026-09-21T10:00:00.100Z"},"objectId":{"${'$'}gt":"o100"}}
                ]}
            ]}""",
            order = PullOrder,
            limit = PageSize,
        )
    }

    @Test
    fun transformReturningNullLeavesObjectOutButMovesMaxUpdatedAt() = runTest {
        val server = FakeParseServer(handler = { respondParse(itemsBody(2)) })

        val page = server.client.pull<Item, String>(className = "Coach", where = JsonObject(emptyMap()), cursor = null) {
            it.objectId.takeIf { id -> id == "o001" }
        }

        assertEquals(listOf("o001"), page.items)
        assertEquals(Instant.parse("2026-09-21T10:00:00.002Z"), page.maxUpdatedAt)
    }

    @Test
    fun pullWhereRestrictsToObjectsUpdatedAfter() {
        val where = pullWhere(updatedAfter = Instant.parse("2026-09-21T10:00:00Z")) { put("active", true) }

        assertEquals(
            """{"updatedAt":{"${'$'}gt":{"iso":"2026-09-21T10:00:00.000Z","__type":"Date"}},"active":true}""",
            where.toString(),
        )
    }

    @Test
    fun cursorRoundTrips() {
        val cursor = PullCursor(updatedAt = Instant.parse("2026-09-21T10:00:00.123Z"), objectId = "aB3dE5gH7j")

        assertEquals(cursor, PullCursor.decode(cursor.encode()))
    }

    @Test
    fun cursorWithoutObjectIdRoundTrips() {
        val cursor = PullCursor(updatedAt = Instant.parse("2026-09-21T10:00:00Z"), objectId = "")

        assertEquals(cursor, PullCursor.decode(cursor.encode()))
    }
}

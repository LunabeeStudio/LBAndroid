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

package studio.lunabee.parse.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ParseModelTest {
    private val json = Json

    @Serializable
    private data class Dated(
        @Serializable(with = ParseDateSerializer::class)
        val at: Instant,
    )

    @Test
    fun dateSerializesWithMillisecondPrecision() {
        val encoded = json.encodeToString(Dated(Instant.parse("2026-09-21T10:00:00.123456789Z")))

        assertEquals("""{"at":{"iso":"2026-09-21T10:00:00.123Z","__type":"Date"}}""", encoded)
    }

    @Test
    fun dateWithoutFractionKeepsMillisecondDigits() {
        val encoded = json.encodeToString(Dated(Instant.parse("2026-09-21T10:00:00Z")))

        assertEquals("""{"at":{"iso":"2026-09-21T10:00:00.000Z","__type":"Date"}}""", encoded)
    }

    @Test
    fun dateParsesFromParseObject() {
        val decoded = json.decodeFromString<Dated>("""{"at":{"__type":"Date","iso":"2026-09-21T10:00:00.123Z"}}""")

        assertEquals(Instant.parse("2026-09-21T10:00:00.123Z"), decoded.at)
    }

    @Test
    fun pointerSerializesWithTypeAndClassName() {
        val encoded = json.encodeToString(ParsePointer(className = "Coach", objectId = "abc"))

        assertEquals("""{"className":"Coach","objectId":"abc","__type":"Pointer"}""", encoded)
    }

    @Test
    fun fileRoundTripsWithTypeFile() {
        val file = ParseFile(name = "avatar.jpg", url = "https://files.test/avatar.jpg")

        val encoded = json.encodeToString(file)

        assertEquals("""{"name":"avatar.jpg","url":"https://files.test/avatar.jpg","__type":"File"}""", encoded)
        assertEquals(file, json.decodeFromString<ParseFile>(encoded))
    }

    @Test
    fun aclSerializesAsMapOfAccess() {
        val acl: ParseACL = mapOf("*" to ParseAccess(read = true), "u1" to ParseAccess(read = true, write = true))

        val encoded = json.encodeToString(acl)

        assertEquals("""{"*":{"read":true},"u1":{"read":true,"write":true}}""", encoded)
    }
}

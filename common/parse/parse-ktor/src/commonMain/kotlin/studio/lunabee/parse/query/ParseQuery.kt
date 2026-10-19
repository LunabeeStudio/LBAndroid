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

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import studio.lunabee.parse.client.LBParseClient
import studio.lunabee.parse.model.ParseDateSerializer
import kotlin.time.Instant

/** Objects fetched per page of a pull. */
@PublishedApi
internal const val PageSize: Int = 100

/** Parse field holding the last update of an object. */
@PublishedApi
internal const val UpdatedAtField: String = "updatedAt"

@PublishedApi
internal const val ObjectIdField: String = "objectId"

/** Order of a pull, by last update then object id, so objects updated at the same time never move between pages. */
@PublishedApi
internal const val PullOrder: String = "$UpdatedAtField,$ObjectIdField"

/**
 * Objects of [className] matching [where], sorted by [order] when set, at most [limit].
 */
suspend inline fun <reified T> LBParseClient.query(
    className: String,
    where: JsonObject,
    order: String?,
    limit: Int,
): List<T> = httpClient.get("classes/$className") {
    parameter("where", where.toString())
    order?.let { parameter("order", it) }
    parameter("limit", limit)
}.body<ParseQueryPage<T>>().results

/**
 * Page of the objects of [className] matching [where], sorted by last update, after [cursor] when set, each mapped by
 * [transform]. Objects [transform] maps to null are left out of the page but still move its [ParsePage.maxUpdatedAt]
 * and [ParsePage.nextCursor]. Pass [ParsePage.nextCursor] back as [cursor] until it is `null`.
 */
suspend inline fun <reified T : ParseObject, R : Any> LBParseClient.pull(
    className: String,
    where: JsonObject,
    cursor: String?,
    noinline transform: (T) -> R?,
): ParsePage<R> {
    val pageWhere = cursor?.let { PullCursor.decode(it).after(where) } ?: where
    return query<T>(className, where = pageWhere, order = PullOrder, limit = PageSize).toParsePage(transform)
}

/**
 * `where` of a pull, restricted to objects updated after [updatedAfter] when set, plus the [constraints] of the class.
 */
fun pullWhere(
    updatedAfter: Instant?,
    constraints: JsonObjectBuilder.() -> Unit = {},
): JsonObject = buildJsonObject {
    updatedAfter?.let { since ->
        put(
            key = UpdatedAtField,
            element = buildJsonObject { put("\$gt", Json.encodeToJsonElement(ParseDateSerializer, since)) },
        )
    }
    constraints()
}

@PublishedApi
internal fun <T : ParseObject, R : Any> List<T>.toParsePage(transform: (T) -> R?): ParsePage<R> = ParsePage(
    items = mapNotNull(transform),
    maxUpdatedAt = mapNotNull { it.updatedAt }.maxOrNull(),
    nextCursor = if (size < PageSize) null else PullCursor.of(last()).encode(),
)

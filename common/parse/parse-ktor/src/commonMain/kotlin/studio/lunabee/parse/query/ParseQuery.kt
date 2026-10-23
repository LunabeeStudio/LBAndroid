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

import io.ktor.client.request.get
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import studio.lunabee.parse.client.LBParseClient
import studio.lunabee.parse.error.LBParseException
import studio.lunabee.parse.model.ParseDateSerializer
import kotlin.time.Instant

/** Objects fetched per page of a pull. */
internal const val PageSize: Int = 100

/** Parse field holding the last update of an object. */
internal const val UpdatedAtField: String = "updatedAt"

internal const val ObjectIdField: String = "objectId"

internal const val WhereParameter: String = "where"

internal const val OrderParameter: String = "order"

internal const val LimitParameter: String = "limit"

internal const val KeysParameter: String = "keys"

internal const val IncludeParameter: String = "include"

internal const val GreaterThanOperator: String = "\$gt"

internal const val OrOperator: String = "\$or"

internal const val AndOperator: String = "\$and"

/** Order of a pull, by last update then object id, so objects updated at the same time never move between pages. */
internal const val PullOrder: String = "$UpdatedAtField,$ObjectIdField"

/**
 * Objects of [className] matching [where], sorted by [order] when set, at most [limit]. Only the fields in [keys] are
 * returned when it is not empty, and the pointers named in [include] are fetched in place.
 */
suspend inline fun <reified T> LBParseClient.query(
    className: String,
    where: JsonObject,
    order: String?,
    limit: Int,
    keys: List<String> = emptyList(),
    include: List<String> = emptyList(),
): List<T> = query(
    className = className,
    where = where,
    order = order,
    limit = limit,
    serializer = serializer<T>(),
    keys = keys,
    include = include,
)

/**
 * Same as the reified [query], for a type known only through its [serializer], such as a class type parameter.
 * A body [serializer] cannot decode fails with [LBParseException.Decoding], converted by
 * [studio.lunabee.parse.client.LBParseConfig.mapErr].
 */
suspend fun <T> LBParseClient.query(
    className: String,
    where: JsonObject,
    order: String?,
    limit: Int,
    serializer: KSerializer<T>,
    keys: List<String> = emptyList(),
    include: List<String> = emptyList(),
): List<T> {
    val body = httpClient.get("classes/$className") {
        queryParameters(where = where, order = order, limit = limit, keys = keys, include = include)
    }.bodyAsText()
    return try {
        ResponseJson.decodeFromString(ParseQueryPage.serializer(serializer), body).results
    } catch (e: SerializationException) {
        throw mapErr(LBParseException.Decoding(cause = e))
    }
}

/**
 * Page of the objects of [className] matching [where], sorted by last update, after [cursor] when set, each mapped by
 * [transform]. Objects [transform] maps to null are left out of the page but still move its [ParsePage.maxUpdatedAt]
 * and [ParsePage.nextCursor]. Pass [ParsePage.nextCursor] back as [cursor] until it is `null`. [keys] and [include]
 * apply to every page, as in [query].
 */
suspend inline fun <reified T : ParseObject, R : Any> LBParseClient.pull(
    className: String,
    where: JsonObject,
    cursor: String?,
    keys: List<String> = emptyList(),
    include: List<String> = emptyList(),
    noinline transform: (T) -> R?,
): ParsePage<R> = pull(
    className = className,
    where = where,
    cursor = cursor,
    serializer = serializer<T>(),
    keys = keys,
    include = include,
    transform = transform,
)

/**
 * Same as the reified [pull], for a type known only through its [serializer], such as a class type parameter.
 */
suspend fun <T : ParseObject, R : Any> LBParseClient.pull(
    className: String,
    where: JsonObject,
    cursor: String?,
    serializer: KSerializer<T>,
    keys: List<String> = emptyList(),
    include: List<String> = emptyList(),
    transform: (T) -> R?,
): ParsePage<R> {
    val pageWhere = cursor?.let { PullCursor.decode(it).after(where) } ?: where
    return query(
        className,
        where = pageWhere,
        order = PullOrder,
        limit = PageSize,
        serializer = serializer,
        keys = keys,
        include = include,
    ).toParsePage(transform)
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
            element = buildJsonObject { put(GreaterThanOperator, Json.encodeToJsonElement(ParseDateSerializer, since)) },
        )
    }
    constraints()
}

internal fun <T : ParseObject, R : Any> List<T>.toParsePage(transform: (T) -> R?): ParsePage<R> = ParsePage(
    items = mapNotNull(transform),
    maxUpdatedAt = mapNotNull { it.updatedAt }.maxOrNull(),
    nextCursor = if (size < PageSize) null else PullCursor.of(last()).encode(),
)

private fun HttpRequestBuilder.queryParameters(
    where: JsonObject,
    order: String?,
    limit: Int,
    keys: List<String>,
    include: List<String>,
) {
    parameter(WhereParameter, where.toString())
    order?.let { parameter(OrderParameter, it) }
    parameter(LimitParameter, limit)
    if (keys.isNotEmpty()) parameter(KeysParameter, keys.joinToString(","))
    if (include.isNotEmpty()) parameter(IncludeParameter, include.joinToString(","))
}

/** Decodes query responses like the Json of the client, built by `LBKtorJson`. */
private val ResponseJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
}

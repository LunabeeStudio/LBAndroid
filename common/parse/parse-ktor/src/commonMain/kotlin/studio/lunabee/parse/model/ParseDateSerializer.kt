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

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

/**
 * Serializes an [Instant] as a Parse `Date` object, `{"__type": "Date", "iso": …}`, in UTC with millisecond precision.
 * Apply it with `@Serializable(with = ParseDateSerializer::class)` on custom Date fields. The `createdAt` and `updatedAt`
 * fields of an object are plain ISO strings in Parse responses: they use the default `Instant` serializer.
 */
object ParseDateSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = ParseDate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeSerializableValue(ParseDate.serializer(), ParseDate(iso = value.toParseIso()))
    }

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeSerializableValue(ParseDate.serializer()).iso)

    private fun Instant.toParseIso(): String {
        val iso = Instant.fromEpochMilliseconds(toEpochMilliseconds()).toString()
        return if ('.' in iso) iso else iso.replace("Z", ".000Z")
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private class ParseDate(
    val iso: String,
    @EncodeDefault
    @SerialName("__type")
    val type: String = "Date",
)

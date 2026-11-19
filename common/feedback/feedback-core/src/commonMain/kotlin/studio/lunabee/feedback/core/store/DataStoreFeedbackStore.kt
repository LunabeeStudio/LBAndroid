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

package studio.lunabee.feedback.core.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import studio.lunabee.feedback.core.model.LBFeedbackItem
import studio.lunabee.feedback.core.model.LBFeedbackMetadata
import studio.lunabee.feedback.core.model.LBFeedbackStatus
import studio.lunabee.feedback.core.model.LBFeedbackType
import kotlin.time.Instant

internal class DataStoreFeedbackStore(
    private val dataStore: DataStore<Preferences>,
) : LBFeedbackStore {
    override val items: Flow<List<LBFeedbackItem>> = dataStore.data.map { readItems(it) }

    override val reporterName: Flow<String?> = dataStore.data.map { it[reporterKey] }

    override suspend fun add(item: LBFeedbackItem) {
        dataStore.edit { writeItems(it, listOf(item) + readItems(it)) }
    }

    override suspend fun update(remoteId: String, transform: (LBFeedbackItem) -> LBFeedbackItem) {
        dataStore.edit { preferences ->
            writeItems(preferences, readItems(preferences).map { if (it.remoteId == remoteId) transform(it) else it })
        }
    }

    override suspend fun remove(remoteId: String) {
        dataStore.edit { preferences ->
            writeItems(preferences, readItems(preferences).filterNot { it.remoteId == remoteId })
        }
    }

    override suspend fun setReporterName(name: String?) {
        dataStore.edit { preferences ->
            if (name == null) preferences.remove(reporterKey) else preferences[reporterKey] = name
        }
    }

    private fun readItems(preferences: Preferences): List<LBFeedbackItem> = preferences[itemsKey]
        ?.let { json.decodeFromString(serializer, it) }
        .orEmpty()
        .mapNotNull(::toItem)

    private fun writeItems(preferences: MutablePreferences, items: List<LBFeedbackItem>) {
        preferences[itemsKey] = json.encodeToString(serializer, items.map(::toStored))
    }

    private fun toStored(item: LBFeedbackItem): StoredFeedbackItem = StoredFeedbackItem(
        remoteId = item.remoteId,
        type = typeToCode(item.type),
        title = item.title,
        body = item.body,
        reporter = item.reporter,
        appVersionName = item.metadata.appVersionName,
        appVersionCode = item.metadata.appVersionCode,
        buildVariant = item.metadata.buildVariant,
        deviceManufacturer = item.metadata.deviceManufacturer,
        deviceModel = item.metadata.deviceModel,
        osVersion = item.metadata.osVersion,
        locale = item.metadata.locale,
        libraryVersion = item.metadata.libraryVersion,
        sentAtMillis = item.metadata.sentAt.toEpochMilliseconds(),
        extras = item.metadata.extras,
        createdAtMillis = item.createdAt.toEpochMilliseconds(),
        statusCode = item.status.code,
    )

    private fun toItem(stored: StoredFeedbackItem): LBFeedbackItem? {
        val type = codeToType(stored.type) ?: return null
        val status = LBFeedbackStatus.fromCode(stored.statusCode) ?: return null
        return LBFeedbackItem(
            remoteId = stored.remoteId,
            type = type,
            title = stored.title,
            body = stored.body,
            reporter = stored.reporter,
            metadata = LBFeedbackMetadata(
                appVersionName = stored.appVersionName,
                appVersionCode = stored.appVersionCode,
                buildVariant = stored.buildVariant,
                deviceManufacturer = stored.deviceManufacturer,
                deviceModel = stored.deviceModel,
                osVersion = stored.osVersion,
                locale = stored.locale,
                libraryVersion = stored.libraryVersion,
                sentAt = Instant.fromEpochMilliseconds(stored.sentAtMillis),
                extras = stored.extras,
            ),
            createdAt = Instant.fromEpochMilliseconds(stored.createdAtMillis),
            status = status,
        )
    }

    private fun typeToCode(type: LBFeedbackType): String = when (type) {
        LBFeedbackType.Bug -> "bug"
        LBFeedbackType.Idea -> "idea"
        LBFeedbackType.Other -> "other"
    }

    private fun codeToType(code: String): LBFeedbackType? = when (code) {
        "bug" -> LBFeedbackType.Bug
        "idea" -> LBFeedbackType.Idea
        "other" -> LBFeedbackType.Other
        else -> null
    }

    private companion object {
        val itemsKey: Preferences.Key<String> = stringPreferencesKey("lbFeedbackItems")
        val reporterKey: Preferences.Key<String> = stringPreferencesKey("lbFeedbackReporterName")
        val json: Json = Json { ignoreUnknownKeys = true }
        val serializer = ListSerializer(StoredFeedbackItem.serializer())
    }
}

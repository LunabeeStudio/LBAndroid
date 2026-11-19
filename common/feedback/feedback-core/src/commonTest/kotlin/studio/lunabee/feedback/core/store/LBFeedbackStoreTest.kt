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
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import studio.lunabee.feedback.core.model.LBFeedbackItem
import studio.lunabee.feedback.core.model.LBFeedbackMetadata
import studio.lunabee.feedback.core.model.LBFeedbackStatus
import studio.lunabee.feedback.core.model.LBFeedbackType
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class LBFeedbackStoreTest {
    private val path: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "lb_feedback_test_${Random.nextLong()}.preferences_pb"

    private fun newStore(scope: CoroutineScope = CoroutineScope(SupervisorJob())): LBFeedbackStore {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(scope = scope) { path }
        return DataStoreFeedbackStore(dataStore)
    }

    private fun fakeItem(
        remoteId: String = "item-1",
        status: LBFeedbackStatus = LBFeedbackStatus.Pending,
    ): LBFeedbackItem = LBFeedbackItem(
        remoteId = remoteId,
        type = LBFeedbackType.Bug,
        title = "Crash on start",
        body = "It crashes\nevery time",
        reporter = "Ada",
        metadata = LBFeedbackMetadata(
            appVersionName = "1.2.3",
            appVersionCode = 42L,
            buildVariant = "devBeta",
            deviceManufacturer = "Google",
            deviceModel = "Pixel 9",
            osVersion = "Android 16",
            locale = "fr-FR",
            libraryVersion = "1.0.0",
            sentAt = Instant.fromEpochMilliseconds(2_000L),
            extras = mapOf("route" to "home"),
        ),
        createdAt = Instant.fromEpochMilliseconds(1_000L),
        status = status,
    )

    @Test
    fun addThenObserveReturnsItem() = runTest {
        val store = newStore()
        val item = fakeItem()

        store.add(item)

        assertEquals(listOf(item), store.items.first())
    }

    @Test
    fun updatePersistsStatus() = runTest {
        val store = newStore()
        store.add(fakeItem())

        store.update("item-1") { it.copy(status = LBFeedbackStatus.Accepted) }

        assertEquals(LBFeedbackStatus.Accepted, store.items.first().single().status)
    }

    @Test
    fun updateTransformsCurrentValue() = runTest {
        val store = newStore()
        store.add(fakeItem())

        store.update("item-1") { it.copy(title = "Edited") }
        store.update("item-1") { it.copy(status = LBFeedbackStatus.Declined) }

        val item = store.items.first().single()
        assertEquals("Edited", item.title)
        assertEquals(LBFeedbackStatus.Declined, item.status)
    }

    @Test
    fun removeDeletesItem() = runTest {
        val store = newStore()
        store.add(fakeItem(remoteId = "a"))
        store.add(fakeItem(remoteId = "b"))

        store.remove("a")

        assertEquals(listOf("b"), store.items.first().map { it.remoteId })
    }

    @Test
    fun reporterNameRoundTrips() = runTest {
        val store = newStore()
        assertNull(store.reporterName.first())

        store.setReporterName("Ada")
        assertEquals("Ada", store.reporterName.first())

        store.setReporterName(null)
        assertNull(store.reporterName.first())
    }

    @Test
    fun storeSurvivesNewInstanceOnSameFile() = runTest {
        val firstScope = CoroutineScope(SupervisorJob())
        newStore(firstScope).add(fakeItem())
        firstScope.cancel()

        val items = newStore().items.first()

        assertEquals(listOf(fakeItem()), items)
    }
}

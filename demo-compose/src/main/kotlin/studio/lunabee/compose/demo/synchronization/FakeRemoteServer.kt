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

package studio.lunabee.compose.demo.synchronization

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import studio.lunabee.compose.demo.synchronization.persistence.FakeRemoteServerDatabase
import studio.lunabee.compose.demo.synchronization.persistence.ServerItemDao
import studio.lunabee.compose.demo.synchronization.persistence.ServerItemEntity
import studio.lunabee.synchronization.connectivity.LBConnectivityManager
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/**
 * A stand-in for a remote backend, backed by a Room database so its state survives process death (the
 * groundwork for a later background-sync demo). It is an application-scoped singleton — obtain it via
 * [getInstance] — so a future background worker and the UI observe the same server.
 *
 * Every call adds a [latency] delay so the demo shows the sync statuses transitioning, and
 * [failNextSync] injects a one-shot error to demonstrate the `*WithError` statuses and the engine's
 * automatic retry.
 */
class FakeRemoteServer private constructor(
    private val dao: ServerItemDao,
    private val isOnline: () -> Boolean,
) {

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val itemsFlow: Flow<List<ServerItem>> = dao.observeAll()
        .map { entities -> entities.map { it.toServerItem() } }

    val items: StateFlow<List<ServerItem>> = itemsFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )

    var latency: Duration = 1_500.milliseconds

    var failNextSync: Boolean = false

    suspend fun addRemote(label: String) {
        dao.upsert(
            listOf(
                ServerItemEntity(
                    id = Uuid.random().toString(),
                    label = label,
                    updatedAt = Clock.System.now().toEpochMilliseconds(),
                ),
            ),
        )
    }

    suspend fun get(id: String): ServerItem? = dao.getById(id)?.toServerItem()

    /** Fetch every server record, ordered by ascending `updatedAt` (as the engine expects). */
    suspend fun fetch(): List<ServerItem> {
        delay(latency)
        requireNetwork()
        failIfRequested()
        return dao.getAll().map { it.toServerItem() }
    }

    suspend fun push(items: List<ServerItem>) {
        delay(latency)
        requireNetwork()
        failIfRequested()
        dao.upsert(items.map(ServerItemEntity::fromServerItem))
    }

    suspend fun touchRemote(id: String) {
        dao.touch(id = id, updatedAt = Clock.System.now().toEpochMilliseconds())
    }

    suspend fun deleteRemote(id: String) {
        dao.delete(id)
    }

    suspend fun clear() {
        dao.clear()
    }

    private fun failIfRequested() {
        if (failNextSync) {
            failNextSync = false
            throw IOException("Simulated network failure")
        }
    }

    private fun requireNetwork() {
        if (!isOnline()) {
            throw IOException("No network: server unreachable")
        }
    }

    companion object {
        @Volatile
        private var instance: FakeRemoteServer? = null

        fun getInstance(context: Context): FakeRemoteServer = instance ?: synchronized(this) {
            val appContext = context.applicationContext
            instance ?: FakeRemoteServer(
                dao = FakeRemoteServerDatabase.getInstance(appContext).serverItemDao(),
                isOnline = { LBConnectivityManager.getNetworkState(appContext).isConnected },
            ).also { instance = it }
        }
    }
}

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

package studio.lunabee.synchronization

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import studio.lunabee.synchronization.LBSyncOperator.startServerNotificationListeners
import studio.lunabee.synchronization.LBSyncOperator.stopServerNotificationListeners
import studio.lunabee.synchronization.syncmanager.LBSyncRefreshEvent
import kotlin.reflect.KClass

actual object LBAppForegroundEventListener : LBSyncEventListener {
    // Lifecycle observation must touch ProcessLifecycleOwner on the main thread. Lazy so merely touching
    // the operator (e.g. triggerRefresh in JVM host tests) never forces Dispatchers.Main to load.
    private val mainScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    private var appLifecycleJob: Job? = null

    private fun appForegroundFlow(): Flow<Boolean> = callbackFlow {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                trySend(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                trySend(false)
            }
        }
        lifecycle.addObserver(observer)
        awaitClose { lifecycle.removeObserver(observer) }
    }

    actual override fun register(
        onEvent: (eventType: KClass<out LBSyncRefreshEvent>) -> Unit,
    ) {
        appLifecycleJob?.cancel()
        appLifecycleJob = mainScope.launch {
            appForegroundFlow()
                .distinctUntilChanged()
                .collect { isForeground ->
                    if (isForeground) {
                        onEvent(LBSyncRefreshEvent.AppForeground::class)
                        startServerNotificationListeners()
                    } else {
                        stopServerNotificationListeners()
                    }
                }
        }
    }
}

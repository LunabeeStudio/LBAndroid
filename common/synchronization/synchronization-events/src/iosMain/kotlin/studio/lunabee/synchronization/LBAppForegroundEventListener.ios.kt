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

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import studio.lunabee.synchronization.LBSyncOperator.startServerNotificationListeners
import studio.lunabee.synchronization.LBSyncOperator.stopServerNotificationListeners
import studio.lunabee.synchronization.syncmanager.LBSyncRefreshEvent
import kotlin.reflect.KClass

@OptIn(ExperimentalForeignApi::class)
actual object LBAppForegroundEventListener : LBSyncEventListener {
    private val mainScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var appLifecycleJob: Job? = null

    private fun appForegroundFlow(): Flow<Boolean> = callbackFlow {
        val notificationCenter = NSNotificationCenter.defaultCenter
        val foregroundObserver = notificationCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) {
            trySend(true)
        }
        val backgroundObserver = notificationCenter.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) {
            trySend(false)
        }
        trySend(UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive)
        awaitClose {
            notificationCenter.removeObserver(foregroundObserver)
            notificationCenter.removeObserver(backgroundObserver)
        }
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

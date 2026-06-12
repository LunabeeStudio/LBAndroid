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

import bolts.Task
import bolts.TaskCompletionSource
import kotlinx.coroutines.launch
import studio.lunabee.core.model.LBResult
import studio.lunabee.synchronization.syncmanager.LBGenericSyncManager

// TODO(#04): remove this whole file once LBSyncGroup/LBSyncOperator stop speaking Bolts.

/**
 * Bridges [LBSyncManager.synchronize][studio.lunabee.synchronization.syncmanager.LBSyncManager.synchronize]
 * to a Bolts [Task] for the still-Bolts [LBSyncGroup] / [LBSyncOperator]. The suspend run is launched in
 * the manager's library scope; success completes the task with `null`, failure sets its error. Lives
 * here (not on the manager) so the coroutine-native engine carries no `bolts` import.
 */
// TODO(#04): remove Bolts bridge
internal fun LBGenericSyncManager.synchronizeBoltsBridge(): Task<Void> {
    val source = TaskCompletionSource<Void>()
    scope.launch {
        when (val result = synchronize()) {
            is LBResult.Success -> source.setResult(null)
            is LBResult.Failure -> source.setError(result.throwable.toBridgeException())
        }
    }
    return source.task
}

/**
 * Bridges
 * [LBSyncManager.startServerNotificationListener][studio.lunabee.synchronization.syncmanager.LBSyncManager.startServerNotificationListener]
 * to a Bolts [Task] for the still-Bolts group/operator.
 */
// TODO(#04): remove Bolts bridge
internal fun LBGenericSyncManager.startServerNotificationListenerBoltsBridge(): Task<Boolean> {
    val source = TaskCompletionSource<Boolean>()
    scope.launch {
        try {
            source.setResult(startServerNotificationListener())
        } catch (e: Exception) {
            source.setError(e)
        }
    }
    return source.task
}

/**
 * Bridges
 * [LBSyncManager.stopServerNotificationListener][studio.lunabee.synchronization.syncmanager.LBSyncManager.stopServerNotificationListener]
 * to a Bolts [Task] for the still-Bolts group/operator.
 */
// TODO(#04): remove Bolts bridge
internal fun LBGenericSyncManager.stopServerNotificationListenerBoltsBridge(): Task<Boolean> {
    val source = TaskCompletionSource<Boolean>()
    scope.launch {
        try {
            source.setResult(stopServerNotificationListener())
        } catch (e: Exception) {
            source.setError(e)
        }
    }
    return source.task
}

/**
 * Maps a sync failure cause to a Bolts-friendly [Exception] for [synchronizeBoltsBridge].
 */
// TODO(#04): remove Bolts bridge
private fun Throwable?.toBridgeException(): Exception = this as? Exception ?: Exception(this)

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

package studio.lunabee.synchronization.runner

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import studio.lunabee.core.model.LBResult

/**
 * Joins concurrent requests sharing a key onto the one already in flight.
 *
 * The first [run] call for a key executes its block; every [run] call for the same key arriving before that
 * block returns awaits it and receives the same [LBResult], without executing its own block. Once the block has
 * returned, the next call for the key executes again. Unlike [SyncRunner], no follow-up run is queued: a joining
 * caller is served by the request already in flight.
 *
 * A cancelled owner resolves its joiners with an [LBResult.Failure] carrying the cancellation, so they never hang.
 *
 * @param Key the request identity, compared with [equals].
 */
internal class SingleFlight<Key> {
    private val mutex: Mutex = Mutex()
    private val inFlight: MutableMap<Key, CompletableDeferred<LBResult<Unit>>> = mutableMapOf()

    /**
     * Executes [block] for [key], or joins the execution already in flight for it.
     *
     * @return the result of the execution this caller ran or joined.
     */
    suspend fun run(key: Key, block: suspend () -> LBResult<Unit>): LBResult<Unit> {
        val ownRequest = CompletableDeferred<LBResult<Unit>>()
        val request = mutex.withLock { inFlight.getOrPut(key) { ownRequest } }
        if (request !== ownRequest) return request.await()

        val outcome = try {
            block()
        } catch (cancellation: CancellationException) {
            settle(key = key, request = ownRequest, outcome = LBResult.Failure(throwable = cancellation))
            throw cancellation
        }
        settle(key = key, request = ownRequest, outcome = outcome)
        return outcome
    }

    private suspend fun settle(key: Key, request: CompletableDeferred<LBResult<Unit>>, outcome: LBResult<Unit>) {
        withContext(NonCancellable) { mutex.withLock { inFlight.remove(key) } }
        request.complete(outcome)
    }
}

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
 * Coalesces concurrent requests sharing a key into one pending request.
 *
 * The first [run] call for a key creates a pending request and executes its block; every [run] call for the same
 * key arriving before that block calls `markStarted` joins it and receives the same [LBResult], without executing
 * its own block. Once started, the request no longer accepts joiners: the next call for the key creates a new
 * pending request. A burst arriving while a request runs therefore costs one follow-up, and a caller never joins a
 * run that may already have read what it asks for.
 *
 * A cancelled owner hands the request over: one of its joiners executes its own block, and the others join that
 * one. A block failing with an exception resolves its joiners with an [LBResult.Failure] carrying it, then rethrows
 * it to its owner.
 *
 * @param Key the request identity, compared with [equals].
 */
internal class RequestCoalescer<Key> {
    private val mutex: Mutex = Mutex()
    private val pending: MutableMap<Key, CompletableDeferred<LBResult<Unit>?>> = mutableMapOf()

    /**
     * Executes [block] for [key], or joins the request for [key] that has not started yet.
     *
     * @param block the request; it calls `markStarted` once it starts the work later callers must not join.
     * @return the result of the request this caller ran or joined.
     */
    suspend fun run(key: Key, block: suspend (markStarted: suspend () -> Unit) -> LBResult<Unit>): LBResult<Unit> {
        val ownRequest = CompletableDeferred<LBResult<Unit>?>()
        val request = mutex.withLock { pending.getOrPut(key) { ownRequest } }
        return if (request === ownRequest) {
            execute(key = key, request = ownRequest, block = block)
        } else {
            request.await() ?: run(key = key, block = block)
        }
    }

    private suspend fun execute(
        key: Key,
        request: CompletableDeferred<LBResult<Unit>?>,
        block: suspend (markStarted: suspend () -> Unit) -> LBResult<Unit>,
    ): LBResult<Unit> {
        var outcome: LBResult<Unit>? = null
        try {
            return block { release(key = key, request = request) }.also { outcome = it }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            outcome = LBResult.Failure(throwable = error)
            throw error
        } finally {
            withContext(NonCancellable) { release(key = key, request = request) }
            request.complete(outcome)
        }
    }

    private suspend fun release(key: Key, request: CompletableDeferred<LBResult<Unit>?>) {
        mutex.withLock {
            if (pending[key] === request) pending.remove(key)
        }
    }
}

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import studio.lunabee.core.model.LBResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Coroutine-based serializer and collapser for a single repeatable sync action.
 *
 * [SyncRunner] guarantees that at most one execution of the supplied work is in flight at any time and
 * that concurrent requests collapse into a single follow-up run instead of piling up. It also retries a
 * failed run automatically after a configurable delay.
 *
 * Concurrency contract:
 * - **Idle run** — calling [run] while nothing is in flight executes the block immediately on [scope];
 *   the caller suspends until it finishes and receives the block's real [LBResult].
 * - **Collapse-and-join** — every [run] call arriving while a run is in flight does *not* start its own
 *   run. All such callers collapse into exactly one follow-up run that starts once the in-flight run
 *   finishes, and each of them receives that single follow-up run's real result. A further wave arriving
 *   during the follow-up collapses into yet another single follow-up, and so on.
 * - **Failure retry** — when a run completes with [LBResult.Failure] and no follow-up run is already
 *   queued, a re-run is scheduled after [retryDelay]. If [retryDelay] returns `null`, retry is disabled.
 *   The retry's result is not returned to anyone (awaiting callers already received the failure); a
 *   failing retry reschedules under the same rule, while a successful run never schedules a retry.
 * - **Pre-emption** — a new [run] request or a [cancel] call aborts any pending (not-yet-fired) retry.
 *
 * All internal state is guarded by a [Mutex]; runs are launched as children of the provided [scope].
 *
 * @param scope the scope every run and retry is launched in. Cancelling it (or calling [cancel]) tears
 * down all work; the runner does not own or close it.
 * @param retryDelay provider for the delay before an automatic retry of a failed run, re-read each time
 * a retry is scheduled so a caller backed by a mutable tempo is always honoured. Return `null` to
 * disable retry. Defaults to 30 seconds.
 */
class SyncRunner(
    private val scope: CoroutineScope,
    private val retryDelay: () -> Duration? = { 30.seconds },
) {

    private val mutex: Mutex = Mutex()

    private var inFlight: RunHandle? = null

    /**
     * The single shared follow-up run that callers arriving during [inFlight] collapse onto, or `null`
     * if no follow-up has been requested for the current in-flight run. Sharing one instance across all
     * collapsed callers is what enforces the "exactly one follow-up" guarantee. The follow-up is not
     * launched until [settle] promotes it, so cancelling it is just completing its [PendingRun.result].
     */
    private var pending: PendingRun? = null

    private var retryJob: Job? = null

    /**
     * Runs [block], honoring the collapse-and-join and retry contract documented on the class.
     *
     * @param block the suspending work to execute. The same reference is reused for the collapsed
     * follow-up run and for automatic retries.
     * @return the real [LBResult] of the run this caller is attached to: the immediate run when idle, or
     * the single shared follow-up run when a run was already in flight.
     */
    suspend fun run(block: suspend () -> LBResult<Unit>): LBResult<Unit> {
        val result: CompletableDeferred<LBResult<Unit>> = mutex.withLock {
            // A new explicit request always pre-empts a pending retry.
            cancelPendingRetryLocked()

            if (inFlight == null) {
                startRunLocked(block, result = CompletableDeferred()).also { inFlight = it }.result
            } else {
                // Busy: collapse onto the single follow-up (creating it once if needed).
                val followUp = pending
                    ?: PendingRun(block = block, result = CompletableDeferred()).also { pending = it }
                followUp.result
            }
        }
        return result.await()
    }

    /**
     * Cancels the in-flight run (if any), the queued follow-up run (if any) and any pending retry.
     * Callers currently awaiting a cancelled run — including collapsed callers whose follow-up never
     * started — observe a completed [LBResult.Failure] carrying the cancellation cause rather than
     * hanging, and the runner stays reusable for later [run] calls.
     */
    fun cancel() {
        scope.launch {
            mutex.withLock {
                cancelPendingRetryLocked()
                inFlight?.let { handle ->
                    handle.job.cancel()
                    handle.result.complete(cancelledFailure())
                }
                // The follow-up was never launched: resolving its awaiters is all there is to cancel.
                pending?.result?.complete(cancelledFailure())
                inFlight = null
                pending = null
            }
        }
    }

    private fun cancelledFailure(): LBResult.Failure<Unit> =
        LBResult.Failure(throwable = CancellationException("Sync run cancelled"))

    /**
     * Launches the coroutine executing a run of [block] and returns its handle. The coroutine runs the
     * block, settles internal state (promotion or retry), then publishes the outcome to [result] —
     * settling *before* completing the result deferred so any awaiter that resumes already sees
     * consistent state. On cancellation it still completes [result] (so awaiters never hang) and still
     * settles, under [NonCancellable]. Must be called while holding [mutex].
     */
    private fun startRunLocked(
        block: suspend () -> LBResult<Unit>,
        result: CompletableDeferred<LBResult<Unit>>,
    ): RunHandle {
        lateinit var handle: RunHandle
        val job = scope.launch {
            val outcome: LBResult<Unit> = try {
                block()
            } catch (cancellation: CancellationException) {
                // Resolve awaiters consistently on cancellation instead of letting them hang, then settle
                // without scheduling a retry — an explicit cancel must not trigger a new run.
                val failure = LBResult.Failure<Unit>(throwable = cancellation)
                withContext(NonCancellable) { settle(handle, failure, block, allowRetry = false) }
                result.complete(failure)
                throw cancellation
            }
            settle(handle, outcome, block, allowRetry = true)
            result.complete(outcome)
        }
        handle = RunHandle(job = job, result = result)
        return handle
    }

    /**
     * Settles internal state once the run owned by [handle] has produced [outcome]: launches a queued
     * follow-up as the new in-flight run, or — when nothing is queued, [allowRetry] is `true` and the
     * run failed — schedules a retry of [block]. Re-reads state under [mutex] so it stays consistent with
     * concurrent [run]/[cancel] calls.
     *
     * @param allowRetry `false` on the cancellation path so an explicit cancel never schedules a retry.
     */
    private suspend fun settle(
        handle: RunHandle,
        outcome: LBResult<Unit>,
        block: suspend () -> LBResult<Unit>,
        allowRetry: Boolean,
    ) {
        mutex.withLock {
            // Ignore stale settlements (e.g. a handle already superseded as in-flight run).
            if (inFlight !== handle) return@withLock

            val followUp = pending
            if (followUp != null) {
                // Promote the collapsed follow-up: launch it now as the in-flight run, completing the
                // result deferred its collapsed callers are already awaiting.
                pending = null
                inFlight = startRunLocked(followUp.block, result = followUp.result)
            } else {
                inFlight = null
                if (allowRetry && outcome is LBResult.Failure) scheduleRetryLocked(block)
            }
        }
    }

    /**
     * Schedules an automatic retry after [retryDelay] (when it yields a non-null duration). The retry
     * executes [block] again on [scope]; its result is discarded. Must be called while holding [mutex].
     */
    private fun scheduleRetryLocked(block: suspend () -> LBResult<Unit>) {
        val delayDuration = retryDelay() ?: return
        retryJob = scope.launch {
            delay(delayDuration)
            mutex.withLock {
                retryJob = null
                // A run started while we waited; let it run instead of duplicating work.
                if (inFlight != null) return@withLock
                inFlight = startRunLocked(block, result = CompletableDeferred())
            }
        }
    }

    /** Cancels and clears any pending retry. Must be called while holding [mutex]. */
    private fun cancelPendingRetryLocked() {
        retryJob?.cancel()
        retryJob = null
    }

    /**
     * Bundles a launched run's state.
     *
     * @property job the coroutine [Job] managing the launched run.
     * @property result the [CompletableDeferred] every awaiter suspends on for the run's [LBResult].
     */
    private class RunHandle(
        val job: Job,
        val result: CompletableDeferred<LBResult<Unit>>,
    )

    /**
     * A collapsed follow-up run that has been requested but not launched yet.
     *
     * @property block the work to execute once promoted.
     * @property result the [CompletableDeferred] every collapsed caller suspends on; completed by the
     * promoted run, or directly with a failure when the runner is cancelled before promotion.
     */
    private class PendingRun(
        val block: suspend () -> LBResult<Unit>,
        val result: CompletableDeferred<LBResult<Unit>>,
    )
}

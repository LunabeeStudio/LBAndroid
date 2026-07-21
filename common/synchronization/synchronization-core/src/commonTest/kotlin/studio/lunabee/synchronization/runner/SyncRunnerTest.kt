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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import studio.lunabee.core.model.LBResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SyncRunnerTest {

    @Test
    fun single_run_executes_block_once_and_returns_its_result() = runRunnerTest { runner ->
        var invocations = 0

        // Launch the caller as a foreground child so virtual time governs the runner's internal launches.
        val caller: Deferred<LBResult<Unit>> = async {
            runner.run {
                invocations++
                LBResult.Success(Unit)
            }
        }
        advanceUntilIdle()

        assertEquals(expected = 1, actual = invocations)
        assertTrue(caller.await() is LBResult.Success, "expected a Success result")
    }

    @Test
    fun concurrent_callers_during_a_run_collapse_into_exactly_one_follow_up() = runRunnerTest { runner ->
        var invocations = 0
        // One gate per invocation index so we can hold each run "in flight" deterministically.
        val gates = List(size = 4) { CompletableDeferred<Unit>() }

        suspend fun block(): LBResult<Unit> {
            val index = invocations++
            gates[index].await()
            return LBResult.Success(Unit)
        }

        val first = async { runner.run(::block) }
        advanceUntilIdle()
        assertEquals(expected = 1, actual = invocations, "only the in-flight run should have started")

        val collapsed = List(size = 5) { async { runner.run(::block) } }
        advanceUntilIdle()
        assertEquals(expected = 1, actual = invocations, "collapsed callers must not start their own run")

        gates[0].complete(Unit)
        advanceUntilIdle()
        assertEquals(expected = 2, actual = invocations, "exactly one follow-up run should have started")

        gates[1].complete(Unit)
        advanceUntilIdle()

        assertTrue(first.await() is LBResult.Success)
        collapsed.forEach {
            assertTrue(it.await() is LBResult.Success, "every collapsed caller gets the follow-up result")
        }
        assertEquals(expected = 2, actual = invocations, "block ran exactly twice total (in-flight + one follow-up)")
    }

    @Test
    fun failure_schedules_a_retry_after_the_configured_delay() = runRunnerTest(retryDelay = { 30.seconds }) { runner ->
        var invocations = 0

        val result = runDirect(runner) {
            invocations++
            LBResult.Failure()
        }

        assertTrue(result is LBResult.Failure)
        assertEquals(expected = 1, actual = invocations, "only the original failing run so far")

        // Before the delay elapses, no retry. runCurrent (not advanceUntilIdle) so we don't over-advance
        // virtual time onto the still-future retry.
        advanceTimeBy(29.seconds)
        runCurrent()
        assertEquals(expected = 1, actual = invocations, "retry must not fire before the delay elapses")

        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(expected = 2, actual = invocations, "the failed run is retried once the delay elapses")
    }

    @Test
    fun success_does_not_schedule_a_retry() = runRunnerTest(retryDelay = { 30.seconds }) { runner ->
        var invocations = 0

        runDirect(runner) {
            invocations++
            LBResult.Success(Unit)
        }
        assertEquals(expected = 1, actual = invocations)

        advanceTimeBy(10.minutes)
        advanceUntilIdle()
        assertEquals(expected = 1, actual = invocations, "a successful run never reruns")
    }

    @Test
    fun null_retry_delay_disables_retry() = runRunnerTest(retryDelay = { null }) { runner ->
        var invocations = 0

        val result = runDirect(runner) {
            invocations++
            LBResult.Failure()
        }
        assertTrue(result is LBResult.Failure)
        assertEquals(expected = 1, actual = invocations)

        advanceTimeBy(10.minutes)
        advanceUntilIdle()
        assertEquals(expected = 1, actual = invocations, "a null retry delay disables the automatic retry")
    }

    @Test
    fun a_new_run_request_before_the_retry_fires_cancels_the_pending_retry() =
        runRunnerTest(retryDelay = { 30.seconds }) { runner ->
            var failingInvocations = 0
            var successInvocations = 0

            runDirect(runner) {
                failingInvocations++
                LBResult.Failure()
            }
            assertEquals(expected = 1, actual = failingInvocations)

            // Halfway through the retry delay a brand-new explicit run arrives: it cancels the pending
            // retry. runCurrent keeps virtual time at +15s so the +30s retry stays pending here.
            advanceTimeBy(15.seconds)
            runCurrent()
            runDirect(runner) {
                successInvocations++
                LBResult.Success(Unit)
            }
            assertEquals(expected = 1, actual = successInvocations)

            advanceTimeBy(30.seconds)
            runCurrent()
            assertEquals(expected = 1, actual = failingInvocations, "the pending retry was cancelled by the new run")
            assertEquals(expected = 1, actual = successInvocations, "the explicit run ran exactly once")
        }

    @Test
    fun cancel_kills_the_in_flight_run_and_a_pending_retry_without_hanging_awaiters() =
        runRunnerTest(retryDelay = { 30.seconds }) { runner ->
            val gate = CompletableDeferred<Unit>()
            var invocations = 0

            val awaiter = async {
                runner.run {
                    invocations++
                    gate.await()
                    LBResult.Success(Unit)
                }
            }
            advanceUntilIdle()
            assertEquals(expected = 1, actual = invocations, "the in-flight run has started and is parked")

            runner.cancel()
            advanceUntilIdle()

            val result = awaiter.await()
            assertTrue(result is LBResult.Failure, "a cancelled in-flight run resolves its awaiter (no deadlock)")

            advanceTimeBy(10.minutes)
            advanceUntilIdle()
            assertEquals(expected = 1, actual = invocations, "cancel must not trigger a retry of the cancelled run")

            val afterCancel = runDirect(runner) { LBResult.Success(Unit) }
            assertTrue(afterCancel is LBResult.Success, "the runner is reusable after cancel()")
        }

    @Test
    fun cancel_resolves_collapsed_follow_up_awaiters_and_keeps_the_runner_reusable() = runRunnerTest { runner ->
        val gate = CompletableDeferred<Unit>()
        var invocations = 0

        val inFlight = async {
            runner.run {
                invocations++
                gate.await()
                LBResult.Success(Unit)
            }
        }
        advanceUntilIdle()
        assertEquals(expected = 1, actual = invocations, "the in-flight run has started and is parked")

        val collapsed = List(size = 3) {
            async {
                runner.run {
                    invocations++
                    LBResult.Success(Unit)
                }
            }
        }
        advanceUntilIdle()

        runner.cancel()
        advanceUntilIdle()

        assertTrue(inFlight.await() is LBResult.Failure, "the cancelled in-flight run resolves its awaiter")
        collapsed.forEach {
            assertTrue(it.await() is LBResult.Failure, "a collapsed caller must not hang when its follow-up is cancelled")
        }
        assertEquals(expected = 1, actual = invocations, "the cancelled follow-up never ran")

        val afterCancel = runDirect(runner) { LBResult.Success(Unit) }
        assertTrue(afterCancel is LBResult.Success, "the runner is reusable after cancelling a queued follow-up")
    }

    @Test
    fun cancel_aborts_a_pending_retry_before_it_fires() = runRunnerTest(retryDelay = { 30.seconds }) { runner ->
        var invocations = 0

        runDirect(runner) {
            invocations++
            LBResult.Failure()
        }
        assertEquals(expected = 1, actual = invocations)

        // A retry is armed; cancel before it fires. runCurrent keeps time at +15s so the retry stays pending.
        advanceTimeBy(15.seconds)
        runCurrent()
        runner.cancel()
        runCurrent()

        advanceTimeBy(30.seconds)
        runCurrent()
        assertEquals(expected = 1, actual = invocations, "cancel() aborts the pending retry")
    }

    @Test
    fun retry_delay_provider_is_re_read_on_each_scheduling() = runTest {
        var currentDelay: Duration = 10.seconds
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val runner = SyncRunner(scope = scope, retryDelay = { currentDelay })
        var invocations = 0

        suspend fun block(): LBResult<Unit> {
            invocations++
            return LBResult.Failure()
        }

        runDirect(runner, ::block)
        assertEquals(expected = 1, actual = invocations)

        // Mutate the tempo before the first retry fires; the provider is re-read at the NEXT scheduling.
        // runCurrent (not advanceUntilIdle) so the second retry — rescheduled by the first — is not also
        // fast-forwarded onto in the same step.
        currentDelay = 5.seconds
        advanceTimeBy(10.seconds)
        runCurrent()
        assertEquals(expected = 2, actual = invocations, "first retry fired at the original 10s tempo")

        advanceTimeBy(4.seconds)
        runCurrent()
        assertEquals(expected = 2, actual = invocations, "second retry honours the updated 5s tempo (not yet)")

        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(expected = 3, actual = invocations, "second retry fired after the updated 5s tempo")

        scope.cancel()
    }

    /**
     * Drives [body] with a [SyncRunner] whose scope runs on the test scheduler, so all of the runner's
     * internal launches and delays are governed by the [TestScope]'s virtual time. The scope is cancelled
     * once [body] returns so [runTest] does not flag the runner's parked/queued coroutines as leaked.
     */
    private fun runRunnerTest(
        retryDelay: () -> Duration? = { 30.seconds },
        body: suspend TestScope.(SyncRunner) -> Unit,
    ) = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val runner = SyncRunner(scope = scope, retryDelay = retryDelay)
        try {
            body(runner)
        } finally {
            scope.cancel()
        }
    }

    /**
     * Issues a single [SyncRunner.run] from a foreground child coroutine and runs the tasks ready at the
     * *current* virtual instant — via [runCurrent], not [advanceUntilIdle] — so an undelayed run settles
     * while any scheduled-but-future retry is left untouched for the test to advance time onto explicitly.
     * Returns the run's result.
     */
    private suspend fun TestScope.runDirect(
        runner: SyncRunner,
        block: suspend () -> LBResult<Unit>,
    ): LBResult<Unit> {
        val caller: Deferred<LBResult<Unit>> = async { runner.run(block) }
        runCurrent()
        return caller.await()
    }
}

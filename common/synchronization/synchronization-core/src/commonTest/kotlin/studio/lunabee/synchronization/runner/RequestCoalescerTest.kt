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
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import studio.lunabee.core.model.LBResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RequestCoalescerTest {

    private val coalescer: RequestCoalescer<String> = RequestCoalescer()

    @Test
    fun a_request_arriving_before_the_pending_one_started_joins_it() = runTest {
        val gate = CompletableDeferred<Unit>()
        var executions = 0
        val failure = LBResult.Failure<Unit>(throwable = IllegalStateException("failed"))
        val first = async {
            coalescer.run(key = "group") { markStarted ->
                executions += 1
                gate.await()
                markStarted()
                failure
            }
        }
        runCurrent()
        val joined = async { coalescer.run(key = "group") { LBResult.Success(Unit).also { executions += 1 } } }
        runCurrent()

        gate.complete(Unit)

        assertSame(expected = failure, actual = first.await(), "the owner receives its block result")
        assertSame(expected = failure, actual = joined.await(), "the joiner receives the owner result")
        assertEquals(expected = 1, actual = executions, "only the owner block ran")
    }

    @Test
    fun requests_arriving_after_the_run_started_coalesce_into_one_follow_up() = runTest {
        val runGate = CompletableDeferred<Unit>()
        val followUpGate = CompletableDeferred<Unit>()
        var executions = 0
        val running = async {
            coalescer.run(key = "group") { markStarted ->
                markStarted()
                executions += 1
                runGate.await()
                LBResult.Success(Unit)
            }
        }
        runCurrent()
        val followUps = List(size = 3) {
            async {
                coalescer.run(key = "group") {
                    executions += 1
                    followUpGate.await()
                    LBResult.Success(Unit)
                }
            }
        }
        runCurrent()

        assertEquals(expected = 2, actual = executions, "a request arriving after the start does not join the run")

        runGate.complete(Unit)
        followUpGate.complete(Unit)
        running.await()
        followUps.forEach { assertTrue(it.await() is LBResult.Success, "every follow-up caller gets the follow-up result") }
        assertEquals(expected = 2, actual = executions, "the burst behind the run cost one follow-up")
    }

    @Test
    fun a_request_after_the_previous_one_ended_runs_again() = runTest {
        var executions = 0

        repeat(times = 2) { coalescer.run(key = "group") { LBResult.Success(Unit).also { executions += 1 } } }

        assertEquals(expected = 2, actual = executions, "each request ran once the previous one had ended")
    }

    @Test
    fun requests_with_different_keys_do_not_join() = runTest {
        val gate = CompletableDeferred<Unit>()
        var executions = 0
        val first = async {
            coalescer.run(key = "first") {
                executions += 1
                gate.await()
                LBResult.Success(Unit)
            }
        }
        runCurrent()

        coalescer.run(key = "second") { LBResult.Success(Unit).also { executions += 1 } }
        gate.complete(Unit)
        first.await()

        assertEquals(expected = 2, actual = executions, "a request for another key ran on its own")
    }

    @Test
    fun a_cancelled_owner_hands_the_request_to_a_joiner() = runTest {
        var executions = 0
        val first = async {
            coalescer.run(key = "group") {
                executions += 1
                CompletableDeferred<LBResult<Unit>>().await()
            }
        }
        runCurrent()
        val joined = async { coalescer.run(key = "group") { LBResult.Success(Unit).also { executions += 1 } } }
        runCurrent()

        first.cancel()

        assertTrue(joined.await() is LBResult.Success, "the joiner ran its own block")
        assertEquals(expected = 2, actual = executions, "the cancelled owner block and the joiner block ran")
    }

    @Test
    fun a_failing_owner_resolves_its_joiners_with_its_error() = runTest {
        val gate = CompletableDeferred<Unit>()
        val error = IllegalStateException("failed")
        val first = async {
            runCatching {
                coalescer.run(key = "group") {
                    gate.await()
                    throw error
                }
            }
        }
        runCurrent()
        val joined = async { coalescer.run(key = "group") { LBResult.Success(Unit) } }
        runCurrent()

        gate.complete(Unit)

        assertSame(expected = error, actual = first.await().exceptionOrNull(), "the owner receives its error")
        assertSame(expected = error, actual = (joined.await() as? LBResult.Failure)?.throwable, "the joiner receives it too")
    }

    @Test
    fun a_request_after_a_failing_owner_runs_again() = runTest {
        runCatching { coalescer.run(key = "group") { throw IllegalStateException("failed") } }
        var executions = 0

        coalescer.run(key = "group") { LBResult.Success(Unit).also { executions += 1 } }

        assertEquals(expected = 1, actual = executions, "the key was released by the failing owner")
    }

    @Test
    fun a_request_arriving_after_the_started_run_ended_joins_the_pending_follow_up() = runTest {
        val runGate = CompletableDeferred<Unit>()
        val followUpGate = CompletableDeferred<Unit>()
        val executed = mutableListOf<String>()
        val running = async {
            coalescer.run(key = "group") { markStarted ->
                markStarted()
                executed += "running"
                runGate.await()
                LBResult.Success(Unit)
            }
        }
        runCurrent()
        val followUpResult = LBResult.Failure<Unit>(throwable = IllegalStateException("follow-up"))
        val followUp = async {
            coalescer.run(key = "group") { markStarted ->
                executed += "follow-up"
                followUpGate.await()
                markStarted()
                followUpResult
            }
        }
        runCurrent()
        runGate.complete(Unit)
        running.await()
        val late = async { coalescer.run(key = "group") { LBResult.Success(Unit).also { executed += "late" } } }
        runCurrent()

        followUpGate.complete(Unit)

        assertSame(expected = followUpResult, actual = followUp.await(), "the follow-up receives its block result")
        assertSame(expected = followUpResult, actual = late.await(), "the late request joined the follow-up")
        assertEquals(expected = listOf("running", "follow-up"), actual = executed, "the late request ran no block of its own")
    }

    @Test
    fun a_cancelled_owner_that_already_started_hands_the_request_to_its_pre_start_joiners() = runTest {
        val startGate = CompletableDeferred<Unit>()
        val runGate = CompletableDeferred<Unit>()
        var joinerExecutions = 0
        val owner = async {
            coalescer.run(key = "group") { markStarted ->
                startGate.await()
                markStarted()
                runGate.await()
                LBResult.Success(Unit)
            }
        }
        val joiner = async {
            coalescer.run(key = "group") {
                joinerExecutions += 1
                LBResult.Success(Unit)
            }
        }

        runCurrent()
        startGate.complete(Unit)
        runCurrent()
        owner.cancel()

        assertTrue(joiner.await() is LBResult.Success, "the joiner received a result")
        assertEquals(expected = 1, actual = joinerExecutions, "the joiner re-ran its own block")
    }
}

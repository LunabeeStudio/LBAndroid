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

class SingleFlightTest {

    private val singleFlight: SingleFlight<String> = SingleFlight()

    @Test
    fun a_request_arriving_while_one_is_in_flight_joins_it() = runTest {
        val gate = CompletableDeferred<Unit>()
        var executions = 0
        val failure = LBResult.Failure<Unit>(throwable = IllegalStateException("failed"))
        val first = async {
            singleFlight.run(key = "group") {
                executions += 1
                gate.await()
                failure
            }
        }
        runCurrent()
        val joined = async { singleFlight.run(key = "group") { LBResult.Success(Unit).also { executions += 1 } } }
        runCurrent()

        gate.complete(Unit)

        assertSame(expected = failure, actual = first.await(), "the owner receives its block result")
        assertSame(expected = failure, actual = joined.await(), "the joiner receives the owner result")
        assertEquals(expected = 1, actual = executions, "only the owner block ran")
    }

    @Test
    fun a_request_after_the_in_flight_one_ended_runs_again() = runTest {
        var executions = 0

        repeat(times = 2) { singleFlight.run(key = "group") { LBResult.Success(Unit).also { executions += 1 } } }

        assertEquals(expected = 2, actual = executions, "each request ran once the previous one had ended")
    }

    @Test
    fun requests_with_different_keys_do_not_join() = runTest {
        val gate = CompletableDeferred<Unit>()
        var executions = 0
        val first = async {
            singleFlight.run(key = "first") {
                executions += 1
                gate.await()
                LBResult.Success(Unit)
            }
        }
        runCurrent()

        singleFlight.run(key = "second") { LBResult.Success(Unit).also { executions += 1 } }
        gate.complete(Unit)
        first.await()

        assertEquals(expected = 2, actual = executions, "a request for another key ran on its own")
    }

    @Test
    fun a_cancelled_owner_resolves_its_joiners_with_a_failure() = runTest {
        val first = async { singleFlight.run(key = "group") { CompletableDeferred<LBResult<Unit>>().await() } }
        runCurrent()
        val joined = async { singleFlight.run(key = "group") { LBResult.Success(Unit) } }
        runCurrent()

        first.cancel()

        assertTrue(joined.await() is LBResult.Failure, "the joiner receives a failure instead of hanging")
    }
}

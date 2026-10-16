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

package studio.lunabee.migration

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import studio.lunabee.core.model.LBResult
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class LBMigrationRunnerTest {

    @Test
    fun run_runs_all_steps_in_order_test() = runTest {
        val calls = mutableListOf<Pair<Int, String>>()
        val store = FakeMigrationVersionStore(version = 0)
        val runner = LBMigrationRunner(
            migrations = listOf(RecordingMigration(1, 2, calls), RecordingMigration(0, 1, calls)),
            lastVersion = 2,
        )

        val result = runner.run(input = "in", versionStore = store)

        assertEquals(LBResult.Success(Unit), result)
        assertEquals(listOf(0 to "in", 1 to "in"), calls)
        assertContentEquals(listOf(1, 2), store.savedVersions)
    }

    @Test
    fun run_at_last_version_does_nothing_test() = runTest {
        val step = RecordingMigration(1, 2)
        val store = FakeMigrationVersionStore(version = 2)
        val runner = LBMigrationRunner(migrations = listOf(step), lastVersion = 2)

        val result = runner.run(input = "in", versionStore = store)

        assertEquals(LBResult.Success(Unit), result)
        assertEquals(0, step.runCount)
        assertEquals(emptyList(), store.savedVersions)
    }

    @Test
    fun run_stops_on_failing_step_and_retries_it_test() = runTest {
        val error = IllegalStateException("boom")
        val first = RecordingMigration(0, 1)
        val failing = RecordingMigration(1, 2, error = error)
        val later = RecordingMigration(2, 3)
        val store = FakeMigrationVersionStore(version = 0)
        val runner = LBMigrationRunner(migrations = listOf(first, failing, later), lastVersion = 3)

        val result = runner.run(input = "in", versionStore = store)
        runner.run(input = "in", versionStore = store)

        assertIs<LBResult.Failure<Unit>>(result)
        assertSame(error, result.throwable)
        assertEquals(1, store.version)
        assertEquals(0, later.runCount)
        assertEquals(2, failing.runCount)
        assertEquals(1, first.runCount)
    }

    @Test
    fun run_rethrows_cancellation_test() = runTest {
        val step = RecordingMigration(0, 1, error = CancellationException("cancelled"))
        val store = FakeMigrationVersionStore(version = 0)
        val runner = LBMigrationRunner(migrations = listOf(step), lastVersion = 1)

        assertFailsWith<CancellationException> {
            runner.run(input = "in", versionStore = store)
        }
        assertEquals(0, store.version)
    }

    @Test
    fun run_fails_on_missing_step_test() = runTest {
        val store = FakeMigrationVersionStore(version = 0)
        val runner = LBMigrationRunner(
            migrations = listOf(RecordingMigration(0, 1), RecordingMigration(2, 3)),
            lastVersion = 3,
        )

        val result = runner.run(input = "in", versionStore = store)

        assertIs<LBResult.Failure<Unit>>(result)
        val exception = assertIs<LBMissingMigrationException>(result.throwable)
        assertEquals(1, exception.fromVersion)
        assertEquals(1, store.version)
    }

    @Test
    fun constructor_rejects_duplicate_start_version_test() {
        assertFailsWith<IllegalArgumentException> {
            LBMigrationRunner(migrations = listOf(RecordingMigration(0, 1), RecordingMigration(0, 2)), lastVersion = 2)
        }
    }

    @Test
    fun constructor_rejects_end_version_not_after_start_test() {
        assertFailsWith<IllegalArgumentException> {
            LBMigrationRunner(migrations = listOf(RecordingMigration(1, 1)), lastVersion = 1)
        }
    }

    @Test
    fun run_passes_input_to_every_step_test() = runTest {
        val first = RecordingMigration(0, 1)
        val second = RecordingMigration(1, 2)
        val runner = LBMigrationRunner(migrations = listOf(first, second), lastVersion = 2)

        runner.run(input = "shared", versionStore = FakeMigrationVersionStore(version = 0))

        assertEquals(listOf("shared"), first.inputs)
        assertEquals(listOf("shared"), second.inputs)
    }
}

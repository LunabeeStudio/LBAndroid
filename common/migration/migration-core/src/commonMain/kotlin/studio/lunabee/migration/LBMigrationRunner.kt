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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import studio.lunabee.core.model.LBResult
import studio.lunabee.logger.LBLogger

private val logger: Logger = LBLogger.get<LBMigrationRunner<*>>()

/**
 * Run [migrations] one after the other until the stored version reaches [lastVersion].
 *
 * @param migrations available steps, each with a distinct [LBMigration.startVersion]
 * @param lastVersion version the data must reach
 * @throws IllegalArgumentException if two steps share a start version or a step does not end after its start
 */
class LBMigrationRunner<Input>(
    migrations: List<LBMigration<Input>>,
    private val lastVersion: Int,
) {
    private val migrationsByStartVersion: Map<Int, LBMigration<Input>> = migrations.associateBy { it.startVersion }

    init {
        require(migrationsByStartVersion.size == migrations.size) { "Migrations must have unique start versions" }
        require(migrations.all { it.endVersion > it.startVersion }) { "Migrations must end after their start version" }
    }

    /**
     * Migrate from the version held by [versionStore] up to the last version, saving the version after each successful step.
     * Stop at the first failure, which leaves the stored version at the last saved step so the next run retries from there.
     *
     * @param input handed to every step
     * @param versionStore where the current version is read and saved
     * @return [LBResult.Failure] carrying the exception of a failing step or version store, or [LBMissingMigrationException]
     * when no step starts at the current version, [LBResult.Success] otherwise
     */
    suspend fun run(input: Input, versionStore: LBMigrationVersionStore): LBResult<Unit> {
        var runningStep: LBMigration<Input>? = null
        return try {
            var version = versionStore.getVersion()
            while (version < lastVersion) {
                val migration = migrationsByStartVersion[version] ?: throw LBMissingMigrationException(version)
                runningStep = migration
                migration.migrate(input)
                runningStep = null
                versionStore.setVersion(migration.endVersion)
                logger.i { "Migration from ${migration.startVersion} to ${migration.endVersion} succeeded" }
                version = migration.endVersion
            }
            LBResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.e(e) { runningStep?.let { "Migration from ${it.startVersion} to ${it.endVersion} failed" } ?: "Migration failed" }
            LBResult.Failure(e)
        }
    }
}

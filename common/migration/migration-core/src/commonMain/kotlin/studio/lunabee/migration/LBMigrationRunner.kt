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

private val logger: Logger = LBLogger.get("LBMigrationRunner")

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
     * Stop at the first failing step, which stays at its start version so the next run retries it.
     *
     * @param input handed to every step
     * @param versionStore where the current version is read and saved
     * @return [LBResult.Failure] carrying the step exception or [LBMissingMigrationException], [LBResult.Success] otherwise
     */
    suspend fun run(input: Input, versionStore: LBMigrationVersionStore): LBResult<Unit> {
        var version = versionStore.getVersion()
        var failure: Throwable? = null
        while (version < lastVersion && failure == null) {
            val migration = migrationsByStartVersion[version]
            if (migration == null) {
                failure = LBMissingMigrationException(version)
            } else {
                failure = runStep(migration, input)
                if (failure == null) {
                    versionStore.setVersion(migration.endVersion)
                    logger.i { "Migration from ${migration.startVersion} to ${migration.endVersion} succeeded" }
                    version = migration.endVersion
                }
            }
        }
        return failure?.let { LBResult.Failure(it) } ?: LBResult.Success(Unit)
    }

    private suspend fun runStep(migration: LBMigration<Input>, input: Input): Throwable? = try {
        migration.migrate(input)
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        logger.e(e) { "Migration from ${migration.startVersion} to ${migration.endVersion} failed" }
        e
    }
}

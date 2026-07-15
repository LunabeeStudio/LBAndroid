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

package studio.lunabee.test

import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

private const val TestDbName: String = "migration-test.db"
private const val LastVersion: Int = 2

// Room's compileSdk (37) is newer than Robolectric 4.16.1 supports, so pin a known SDK for the JVM run.
private const val RobolectricSdk: Int = 34

/** Correct migration: adds the nullable `description` column expected by version 2. */
private val migrationOneToTwo: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `Item` ADD COLUMN `description` TEXT")
    }
}

/** Runs without error but produces a schema that does not match version 2 (wrong column name). */
private val migrationOneToTwoWrongSchema: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `Item` ADD COLUMN `wrong` TEXT")
    }
}

/**
 * Recreates `Item` with the version 2 schema but drops `name` to NULL while copying rows. The
 * resulting schema is correct, so an empty database migrates fine, but the NULL breaks the `name`
 * NOT NULL constraint as soon as a row exists - the kind of bug only a filled-database check catches.
 */
private val migrationNullingNotNullColumnOnRecreate: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE `Item_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `description` TEXT)",
        )
        connection.execSQL("INSERT INTO `Item_new` (`id`, `name`) SELECT `id`, NULL FROM `Item`")
        connection.execSQL("DROP TABLE `Item`")
        connection.execSQL("ALTER TABLE `Item_new` RENAME TO `Item`")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [RobolectricSdk])
class GlobalRoomMigrationTestHelperTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @BeforeTest
    fun cleanDatabase() {
        instrumentation.targetContext.deleteDatabase(TestDbName)
    }

    private fun globalHelper(migrations: List<Migration>): GlobalRoomMigrationTestHelper =
        GlobalRoomMigrationTestHelper(
            testDbName = TestDbName,
            migrationTestHelper = MigrationTestHelper(
                instrumentation = instrumentation,
                file = instrumentation.targetContext.getDatabasePath(TestDbName),
                driver = AndroidSQLiteDriver(),
                databaseClass = MigrationTestDatabase::class,
            ),
            lastVersion = LastVersion,
            migrations = migrations,
        )

    @Test
    fun validMigrationPassesEmptyAndFilledChecks() {
        val helper = globalHelper(migrations = listOf(migrationOneToTwo))
        helper.emptyDBFirstToLastMigration()
        helper.filledDBAllVersionsMigration()
    }

    @Test
    fun missingMigrationFailsEmptyCheck() {
        val helper = globalHelper(migrations = emptyList())
        assertFailsWith<IllegalStateException> {
            helper.emptyDBFirstToLastMigration()
        }
    }

    @Test
    fun wrongResultingSchemaFailsEmptyCheck() {
        val helper = globalHelper(migrations = listOf(migrationOneToTwoWrongSchema))
        assertFailsWith<IllegalStateException> {
            helper.emptyDBFirstToLastMigration()
        }
    }

    @Test
    fun migrationBreakingOnExistingDataPassesEmptyButFailsFilled() {
        // Same migration: the empty-database check cannot see the bug...
        globalHelper(migrations = listOf(migrationNullingNotNullColumnOnRecreate)).emptyDBFirstToLastMigration()
        // ...but the filled-database check does, because the copied row violates the NOT NULL constraint.
        assertFails {
            globalHelper(migrations = listOf(migrationNullingNotNullColumnOnRecreate)).filledDBAllVersionsMigration()
        }
    }
}

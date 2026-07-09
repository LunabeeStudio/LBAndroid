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
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry

class GlobalRoomMigrationTestHelper(
    private val testDbName: String,
    private val migrationTestHelper: MigrationTestHelper,
    private val lastVersion: Int,
    private val migrations: List<Migration>,
) {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    fun emptyDBFirstToLastMigration() {
        migrationTestHelper.createDatabase(1).close()
        migrationTestHelper.runMigrationsAndValidate(lastVersion, migrations).close()
    }

    fun filledDBAllVersionsMigration() {
        for (startVersion in 1 until lastVersion) {
            deleteDatabase()
            migrationTestHelper.createDatabase(startVersion).use { connection ->
                insertSampleRowsInAllTables(connection)
            }
            migrationTestHelper.runMigrationsAndValidate(lastVersion, migrations).close()
        }
    }

    private fun deleteDatabase() {
        instrumentation.targetContext.deleteDatabase(testDbName)
    }

    private fun insertSampleRowsInAllTables(connection: SQLiteConnection) {
        connection.execSQL("PRAGMA foreign_keys=OFF")
        val tables = mutableListOf<String>()
        connection.prepare(
            """
            SELECT name FROM sqlite_master
            WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table','android_metadata')
            """.trimIndent(),
        ).use { statement ->
            while (statement.step()) {
                tables.add(statement.getText(0))
            }
        }
        for (table in tables) {
            insertSampleRow(connection, table)
        }
        connection.execSQL("PRAGMA foreign_keys=ON")
    }

    private fun insertSampleRow(connection: SQLiteConnection, table: String) {
        val columns = mutableListOf<ColumnInfo>()
        connection.prepare("PRAGMA table_info(`$table`)").use { statement ->
            while (statement.step()) {
                val name = statement.getText(1)
                val type = statement.getText(2)
                val notNull = statement.getLong(3) == 1L
                val defaultValue = if (statement.isNull(4)) null else statement.getText(4)
                val primaryKey = statement.getLong(5) == 1L
                columns.add(ColumnInfo(name, type, notNull, defaultValue, primaryKey))
            }
        }

        val requiredColumns = columns.filter { column ->
            column.notNull && column.defaultValue == null
        }

        if (requiredColumns.isEmpty()) {
            connection.execSQL("INSERT INTO `$table` DEFAULT VALUES")
            return
        }

        val columnList = requiredColumns.joinToString(",") { "`${it.name}`" }
        val valuesList = requiredColumns.joinToString(",") { it.sampleValueSql() }
        connection.execSQL("INSERT INTO `$table` ($columnList) VALUES ($valuesList)")
    }

    private data class ColumnInfo(
        val name: String,
        val type: String,
        val notNull: Boolean,
        val defaultValue: String?,
        val primaryKey: Boolean,
    ) {
        fun sampleValueSql(): String {
            val upperType = type.uppercase()
            return when {
                upperType.contains("INT") -> "0"
                upperType.contains("LONG") -> "0L"
                upperType.contains("CHAR") || upperType.contains("CLOB") || upperType.contains("TEXT") -> "'test'"
                upperType.contains("BLOB") -> "X''"
                upperType.contains("REAL") || upperType.contains("FLOA") || upperType.contains("DOUB") -> "0.0"
                else -> "0"
            }
        }
    }
}

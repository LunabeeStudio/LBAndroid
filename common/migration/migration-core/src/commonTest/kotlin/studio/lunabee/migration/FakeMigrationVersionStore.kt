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

class FakeMigrationVersionStore(
    var version: Int,
    private val getError: Throwable? = null,
    private val setError: Throwable? = null,
) : LBMigrationVersionStore {
    val savedVersions: MutableList<Int> = mutableListOf()

    override suspend fun getVersion(): Int = getError?.let { throw it } ?: version

    override suspend fun setVersion(version: Int) {
        setError?.let { throw it }
        this.version = version
        savedVersions += version
    }
}

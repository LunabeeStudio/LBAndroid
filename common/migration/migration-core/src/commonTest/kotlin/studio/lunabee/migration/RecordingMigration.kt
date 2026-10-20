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

class RecordingMigration(
    override val startVersion: Int,
    override val endVersion: Int,
    private val calls: MutableList<Pair<Int, String>> = mutableListOf(),
    private val error: Throwable? = null,
) : LBMigration<String> {
    val inputs: MutableList<String> = mutableListOf()
    var runCount: Int = 0
        private set

    override suspend fun migrate(input: String) {
        runCount++
        inputs += input
        calls += startVersion to input
        error?.let { throw it }
    }
}

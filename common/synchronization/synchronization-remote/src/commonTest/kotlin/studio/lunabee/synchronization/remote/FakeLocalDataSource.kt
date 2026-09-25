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

package studio.lunabee.synchronization.remote

/**
 * In-memory store holding [toPush] as the local changes, recording the [savedDownloads] and the [pushed] items.
 */
internal class FakeLocalDataSource(
    private val toPush: List<Item> = emptyList(),
) : LBSyncLocalDataSource<Item> {
    val savedDownloads: MutableList<List<Item>> = mutableListOf()
    val pushed: MutableList<Item> = mutableListOf()
    var clearCount: Int = 0
        private set

    override suspend fun savePulled(objects: List<Item>) {
        savedDownloads += objects
    }

    override suspend fun clear() {
        clearCount += 1
    }

    override suspend fun objectsToPush(): List<Item> = toPush - pushed.toSet()

    override suspend fun markPushed(pushed: Item) {
        this.pushed += pushed
    }
}
